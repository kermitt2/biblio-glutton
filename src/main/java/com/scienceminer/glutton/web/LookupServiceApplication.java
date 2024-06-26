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

import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.eclipse.jetty.servlets.QoSFilter;

import org.apache.commons.lang3.ArrayUtils;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterRegistration;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
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

        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        Runnable task = new IncrementalLoaderTask(metadataLookup, 
                                                  metadataLookup.getLastIndexed(), 
                                                  configuration, 
                                                  meter, 
                                                  counterInvalidRecords,
                                                  counterIndexedRecords,
                                                  counterFailedIndexedRecords,
                                                  true, // with indexing
                                                  true); // this is daily incremental update

        ScheduledFuture<?> scheduledFuture = executor.scheduleAtFixedRate(task, initalDelay, TimeUnit.DAYS.toSeconds(1), TimeUnit.SECONDS);
    }

    @Override
    public void run(LookupConfiguration configuration, Environment environment) throws Exception {
        String allowedOrigins = configuration.getCorsAllowedOrigins();
        String allowedMethods = configuration.getCorsAllowedMethods();
        String allowedHeaders = configuration.getCorsAllowedHeaders();

        // Enable CORS headers
        final FilterRegistration.Dynamic cors =
            environment.servlets().addFilter("CORS", CrossOriginFilter.class);

        // Configure CORS parameters
        cors.setInitParameter(CrossOriginFilter.ALLOWED_ORIGINS_PARAM, allowedOrigins);
        cors.setInitParameter(CrossOriginFilter.ALLOWED_METHODS_PARAM, allowedMethods);
        cors.setInitParameter(CrossOriginFilter.ALLOWED_HEADERS_PARAM, allowedHeaders);

        // Add URL mapping
        cors.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/*");

        // Enable QoS filter
        /*final FilterRegistration.Dynamic qos = environment.servlets().addFilter("QOS", QoSFilter.class);
        qos.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/*");
        qos.setInitParameter("maxRequests", String.valueOf(configuration.getMaxAcceptedRequests()));*/

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
        bootstrap.addCommand(new LoadUnpayWallCommand());
        bootstrap.addCommand(new LoadIstexIdsCommand());
        bootstrap.addCommand(new LoadPMIDCommand());
        bootstrap.addCommand(new LoadCrossrefCommand());
        bootstrap.addCommand(new GapUpdateCrossrefCommand());
        bootstrap.addCommand(new LoadHALCommand());
        bootstrap.addCommand(new IndexCommand());
        bootstrap.addCommand(new HALAuditCommand());
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
