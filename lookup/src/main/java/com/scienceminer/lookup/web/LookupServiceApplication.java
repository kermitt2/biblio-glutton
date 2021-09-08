package com.scienceminer.lookup.web;

import com.google.common.collect.Lists;
import com.google.inject.Module;

import com.scienceminer.lookup.command.LoadCrossrefCommand;
import com.scienceminer.lookup.command.GapUpdateCrossrefCommand;
import com.scienceminer.lookup.command.LoadIstexIdsCommand;
import com.scienceminer.lookup.command.LoadPMIDCommand;
import com.scienceminer.lookup.command.LoadUnpayWallCommand;
import com.scienceminer.lookup.configuration.LookupConfiguration;
import com.scienceminer.lookup.web.healthcheck.LookupHealthCheck;
import com.scienceminer.lookup.web.module.LookupServiceModule;
import com.scienceminer.lookup.web.module.NotFoundExceptionMapper;
import com.scienceminer.lookup.web.module.ServiceExceptionMapper;
import com.scienceminer.lookup.web.module.ServiceOverloadedExceptionMapper;

import io.dropwizard.Application;
import io.dropwizard.forms.MultiPartBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import com.hubspot.dropwizard.guicier.GuiceBundle;

import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.eclipse.jetty.servlets.QoSFilter;

import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.io.File;

import org.apache.commons.lang3.ArrayUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LookupServiceApplication extends Application<LookupConfiguration> {
    private static final Logger LOGGER = LoggerFactory.getLogger(LookupConfiguration.class);
    private static final String RESOURCES = "/service";
    private static final String[] DEFAULT_CONF_LOCATIONS = {"../config/glutton.yml"};

    // ========== Application ==========
    @Override
    public String getName() {
        return "lookup-service";
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
        final FilterRegistration.Dynamic qos = environment.servlets().addFilter("QOS", QoSFilter.class);
        qos.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/*");
        qos.setInitParameter("maxRequests", String.valueOf(configuration.getMaxAcceptedRequests()));

        environment.jersey().setUrlPattern(RESOURCES + "/*");
        environment.jersey().register(new ServiceExceptionMapper());
        environment.jersey().register(new NotFoundExceptionMapper());
        environment.jersey().register(new ServiceOverloadedExceptionMapper());

        final LookupHealthCheck healthCheck = new LookupHealthCheck(configuration);
        environment.healthChecks().register("HealthCheck", healthCheck);
    }

    private List<? extends Module> getGuiceModules() {
        return Lists.newArrayList(new LookupServiceModule());
    }

    @Override
    public void initialize(Bootstrap<LookupConfiguration> bootstrap) {
        GuiceBundle<LookupConfiguration> guiceBundle = GuiceBundle.defaultBuilder(LookupConfiguration.class)
                .modules(getGuiceModules())
                .build();
        bootstrap.addBundle(guiceBundle);
        bootstrap.addBundle(new MultiPartBundle());
        bootstrap.addCommand(new LoadUnpayWallCommand());
        bootstrap.addCommand(new LoadIstexIdsCommand());
        bootstrap.addCommand(new LoadPMIDCommand());
        bootstrap.addCommand(new LoadCrossrefCommand());
        bootstrap.addCommand(new GapUpdateCrossrefCommand());
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
