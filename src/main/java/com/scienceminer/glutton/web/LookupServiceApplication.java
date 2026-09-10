package com.scienceminer.glutton.web;

import com.google.common.collect.Lists;
import com.google.inject.Module;

import com.scienceminer.glutton.command.*;
import com.scienceminer.glutton.configuration.LookupConfiguration;
import com.scienceminer.glutton.web.healthcheck.LookupHealthCheck;
import com.scienceminer.glutton.web.module.LookupServiceModule;
import com.scienceminer.glutton.web.module.NotFoundExceptionMapper;
import com.scienceminer.glutton.web.module.ServiceExceptionMapper;
import com.scienceminer.glutton.web.module.ServiceOverloadedExceptionMapper;
import com.scienceminer.glutton.utils.crossrefclient.IncrementalLoaderTask;
import com.scienceminer.glutton.storage.lookup.CrossrefMetadataLookup;
import com.scienceminer.glutton.storage.StorageEnvFactory;

import io.dropwizard.forms.MultiPartBundle;
import io.dropwizard.core.Application;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;

//import com.hubspot.dropwizard.guicier.GuiceBundle;
import ru.vyarus.dropwizard.guice.GuiceBundle;
import com.google.inject.AbstractModule;

import org.eclipse.jetty.server.handler.CrossOriginHandler;
import org.eclipse.jetty.server.handler.QoSHandler;

import org.apache.commons.lang3.ArrayUtils;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LookupServiceApplication extends Application<LookupConfiguration> {
    private static final Logger LOGGER = LoggerFactory.getLogger(LookupConfiguration.class);
    private static final String RESOURCES = "/service";
    private static final String ANY_ORIGIN = "*";
    private static final String[] DEFAULT_CONF_LOCATIONS = {"config/glutton.yml"};

    // ========== Application ==========
    @Override
    public String getName() {
        return "lookup-service";
    }

    private void scheduleDailyUpdate(LookupConfiguration configuration, StorageEnvFactory storageEnvFactory) throws Exception {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of(configuration.getTimeZone()));
        String dailyTime = configuration.getDailyUpdateTime();

        if (dailyTime.length() != 5) {
            throw new Exception("Invalid format for Daily Update Time in configuration file, it should be HH:MM");
        }

        String hourString = dailyTime.substring(0,2);
        String minuteString = dailyTime.substring(3,dailyTime.length());

        int hour = -1;
        int min = -1;
        try {
            hour = Integer.parseInt(hourString);
            min = Integer.parseInt(minuteString);
        } catch(Exception e) {
            throw new Exception("Cannot parse Daily Update Time in configuration file, it should be HH:MM", e);
        }

        ZonedDateTime nextRun = now.withHour(hour).withMinute(min).withSecond(0);
        if(now.compareTo(nextRun) > 0)
            nextRun = nextRun.plusDays(1);

        Duration duration = Duration.between(now, nextRun);
        long initalDelay = duration.getSeconds();

        //StorageEnvFactory storageEnvFactory = new StorageEnvFactory(configuration);
        CrossrefMetadataLookup metadataLookup = CrossrefMetadataLookup.getInstance(storageEnvFactory);

        final MetricRegistry metrics = new MetricRegistry();
        final Meter meter = metrics.meter("crossref_daily_update_loading");
        final Counter counterInvalidRecords = metrics.counter("crossref_daily_update_rejected_records");
        final Counter counterIndexedRecords = metrics.counter("crossref_gap_update_indexed_records");
        final Counter counterFailedIndexedRecords = metrics.counter("crossref_gap_update_failed_indexed_records");
        final Meter openAccessMeter = metrics.meter("openAccess_daily_update_storing");
        final Counter counterDroppedOpenAccess = metrics.counter("openAccess_daily_update_dropped_dois");

        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1, runnable -> {
            Thread thread = new Thread(runnable, "crossref-daily-update");
            thread.setDaemon(true);
            return thread;
        });
        IncrementalLoaderTask task = new IncrementalLoaderTask(metadataLookup, 
                                                  metadataLookup.getLastIndexed(), 
                                                  configuration, 
                                                  meter, 
                                                  counterInvalidRecords,
                                                  counterIndexedRecords,
                                                  counterFailedIndexedRecords,
                                                  openAccessMeter,
                                                  counterDroppedOpenAccess,
                                                  true, // with indexing
                                                  true); // this is daily incremental update

        // an exception escaping the task would silently cancel every run after it
        Runnable guarded = () -> {
            try {
                task.run();
                if (!task.isLastRunCompleted()) {
                    LOGGER.error("The daily Crossref update did not complete, it will be attempted again tomorrow");
                }
            } catch (RuntimeException e) {
                LOGGER.error("The daily Crossref update failed, it will be attempted again tomorrow", e);
            }
        };
        LOGGER.info("Daily Crossref update scheduled at " + dailyTime + " " + configuration.getTimeZone()
                + ", first run in " + Duration.ofSeconds(initalDelay).toMinutes() + " minute(s)");
        ScheduledFuture<?> scheduledFuture = executor.scheduleAtFixedRate(guarded, initalDelay, TimeUnit.DAYS.toSeconds(1), TimeUnit.SECONDS);
    }

    @Override
    public void run(LookupConfiguration configuration, Environment environment) throws Exception {
        String allowedOrigins = configuration.getCorsAllowedOrigins();
        String allowedMethods = configuration.getCorsAllowedMethods();
        String allowedHeaders = configuration.getCorsAllowedHeaders();

        // Enable CORS headers. Jetty 12 (the version Dropwizard 5 runs on) deprecated the
        // servlet filters of the jetty-servlets module for removal in favour of these handlers,
        // which sit in front of the servlet context instead of inside its filter chain.
        // Note that allowed origins are regular expressions here, where CrossOriginFilter used
        // comma-separated origins with '*' wildcards; "*" keeps meaning "any origin".
        final CrossOriginHandler cors = new CrossOriginHandler();
        cors.setAllowedOriginPatterns(toAllowedOriginPatterns(allowedOrigins));
        cors.setAllowedMethods(splitConfigList(allowedMethods));
        cors.setAllowedHeaders(splitConfigList(allowedHeaders));
        // CrossOriginHandler defaults to 60s where CrossOriginFilter defaulted to 30min; keep the
        // longer window so browsers do not re-issue a preflight every minute.
        cors.setPreflightMaxAge(Duration.ofSeconds(1800));
        environment.getApplicationContext().insertHandler(cors);

        // Enable QoS handler
        /*final QoSHandler qos = new QoSHandler();
        qos.setMaxRequestCount(configuration.getMaxAcceptedRequests());
        environment.getApplicationContext().insertHandler(qos);*/

        environment.jersey().setUrlPattern(RESOURCES + "/*");
        environment.jersey().register(new ServiceExceptionMapper());
        environment.jersey().register(new NotFoundExceptionMapper());
        environment.jersey().register(new ServiceOverloadedExceptionMapper());

        StorageEnvFactory storageEnvFactory = new StorageEnvFactory(configuration);
        final LookupHealthCheck healthCheck = new LookupHealthCheck(configuration, storageEnvFactory);
        environment.healthChecks().register("HealthCheck", healthCheck);

        scheduleDailyUpdate(configuration, storageEnvFactory);
    }

    /*private List<? extends Module> getGuiceModules() {
        return Lists.newArrayList(new LookupServiceModule());
    }*/

    private AbstractModule getGuiceModules() {
        return new LookupServiceModule();
    }

    /**
     * The CORS settings are comma-separated lists in the YAML configuration, while the Jetty
     * handlers take sets of values.
     */
    private static Set<String> splitConfigList(String value) {
        return Stream.of(value.split(","))
            .map(String::trim)
            .filter(item -> !item.isEmpty())
            .collect(Collectors.toSet());
    }

    /**
     * Translates the configured {@code corsAllowedOrigins} into the patterns
     * {@link CrossOriginHandler} expects.
     * <p>
     * The setting predates Jetty 12 and follows what {@code CrossOriginFilter} accepted: the
     * literal {@code *}, a glob such as {@code https://*.example.com}, or an exact origin.
     * {@code CrossOriginHandler} instead reads every entry other than {@code *} as a regular
     * expression, so entries have to be translated or they change meaning:
     * <ul>
     *     <li>a glob would stop matching — {@code https://*.example.com} is not a regex that
     *         matches {@code https://api.example.com};</li>
     *     <li>an exact origin would start matching too much — in {@code https://api.example.com}
     *         each {@code .} would match any character.</li>
     * </ul>
     * Package-private for testing.
     */
    static Set<String> toAllowedOriginPatterns(String configuredOrigins) {
        return splitConfigList(configuredOrigins).stream()
            .map(LookupServiceApplication::toOriginPattern)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String toOriginPattern(String origin) {
        if (ANY_ORIGIN.equals(origin)) {
            // CrossOriginHandler gives "*" its own meaning: allow any origin.
            return ANY_ORIGIN;
        }
        StringBuilder pattern = new StringBuilder();
        // -1 keeps the trailing empty segments, so a trailing "*" still becomes ".*".
        String[] literals = origin.split("\\*", -1);
        for (int i = 0; i < literals.length; i++) {
            if (i > 0) {
                // Greedy, as CrossOriginFilter was, so one "*" spans several subdomains.
                pattern.append(".*");
            }
            if (!literals[i].isEmpty()) {
                pattern.append(Pattern.quote(literals[i]));
            }
        }
        return pattern.toString();
    }

    @Override
    public void initialize(Bootstrap<LookupConfiguration> bootstrap) {
        /*GuiceBundle<LookupConfiguration> guiceBundle = GuiceBundle.defaultBuilder(LookupConfiguration.class)
                .modules(getGuiceModules())
                .build();*/

        GuiceBundle guiceBundle = GuiceBundle.builder()
            .modules(getGuiceModules())
            .build();

        bootstrap.addBundle(guiceBundle);
        bootstrap.addBundle(new MultiPartBundle());
        bootstrap.addCommand(new LoadIstexIdsCommand());
        bootstrap.addCommand(new LoadPMIDCommand());
        bootstrap.addCommand(new LoadCrossrefCommand());
        bootstrap.addCommand(new GapUpdateCrossrefCommand());
        bootstrap.addCommand(new LoadHALCommand());
        bootstrap.addCommand(new IndexCommand());
        bootstrap.addCommand(new HALAuditCommand());
        bootstrap.addCommand(new LoadOpenAlexCommand());
    }

    public static void main(String... args) throws Exception {
        if (ArrayUtils.getLength(args) < 2) {
            // use default configuration file
            String foundConf = null;
            for (String p : DEFAULT_CONF_LOCATIONS) {
                File confLocation = new File(p).getAbsoluteFile();
                if (confLocation.exists()) {
                    foundConf = confLocation.getAbsolutePath();
                    LOGGER.info("Found conf path: {}", foundConf);
                    break;
                }
            }

            if (foundConf != null) {
                LOGGER.info("Running with default arguments: \"server\" \"{}\"", foundConf);
                args = new String[]{"server", foundConf};
            } else {
                throw new RuntimeException("No explicit config provided and cannot find in one of the default locations: "
                    + Arrays.toString(DEFAULT_CONF_LOCATIONS));
            }
        }
        new LookupServiceApplication().run(args);
    }
}
