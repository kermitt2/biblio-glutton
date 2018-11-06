package web;


import com.google.common.collect.Lists;
import com.google.inject.Module;
import com.hubspot.dropwizard.guicier.GuiceBundle;
import io.dropwizard.Application;
import io.dropwizard.forms.MultiPartBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import web.configuration.LookupConfiguration;
import web.healthcheck.LookupHealthCheck;
import web.module.LookupServiceModule;
import web.module.ServiceExceptionMapper;

import java.util.List;

public final class LookupServiceApplication extends Application<LookupConfiguration> {
    private static final String RESOURCES = "/service";

    // ========== Application ==========
    @Override
    public String getName() {
        return "lookup-service";
    }

    @Override
    public void run(LookupConfiguration lookupConfiguration, Environment environment) throws Exception {

        environment.jersey().setUrlPattern(RESOURCES + "/*");
        environment.jersey().register(new ServiceExceptionMapper());

        final LookupHealthCheck healthCheck = new LookupHealthCheck();
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
        bootstrap.addCommand(new LoadUnpaidWallCommand());
        bootstrap.addCommand(new LoadIstexIdsCommand());
        bootstrap.addCommand(new LoadPMIDCommand());
    }

    // ========== static ==========
    public static void main(String... args) throws Exception {
        new LookupServiceApplication().run(args);
    }
}
