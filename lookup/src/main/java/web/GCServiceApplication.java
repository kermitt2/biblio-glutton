package web;


import com.google.common.collect.Lists;
import com.google.inject.Module;
import com.hubspot.dropwizard.guicier.GuiceBundle;
import io.dropwizard.Application;
import io.dropwizard.forms.MultiPartBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import web.configuration.GCConfiguration;
import web.healthcheck.GCHealthCheck;
import web.module.GCServiceModule;

import java.util.List;

public final class GCServiceApplication extends Application<GCConfiguration> {
    private static final String RESOURCES = "/service";


    // ========== Application ==========
    @Override
    public String getName() {
        return "grobid-consolidation";
    }

    @Override
    public void run(GCConfiguration nerdKidConfiguration, Environment environment) throws Exception {
        environment.jersey().setUrlPattern(RESOURCES + "/*");

        final GCHealthCheck healthCheck = new GCHealthCheck();
        environment.healthChecks().register("GCHealth", healthCheck);

    }

    private List<? extends Module> getGuiceModules() {
        return Lists.newArrayList(new GCServiceModule());
    }


    @Override
    public void initialize(Bootstrap<GCConfiguration> bootstrap) {
        GuiceBundle<GCConfiguration> guiceBundle = GuiceBundle.defaultBuilder(GCConfiguration.class)
                .modules(getGuiceModules())
                .build();
        bootstrap.addBundle(guiceBundle);
        bootstrap.addBundle(new MultiPartBundle());

        bootstrap.addCommand(new LoadCommand());
    }

    // ========== static ==========
    public static void main(String... args) throws Exception {
        new GCServiceApplication().run(args);
    }
}
