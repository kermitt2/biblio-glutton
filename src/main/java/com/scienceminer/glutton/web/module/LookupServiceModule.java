package com.scienceminer.glutton.web.module;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Binder;
import com.google.inject.Provides;
//import com.hubspot.dropwizard.guicier.DropwizardAwareModule;
import ru.vyarus.dropwizard.guice.module.support.DropwizardAwareModule;
import com.scienceminer.glutton.configuration.LookupConfiguration;
import com.scienceminer.glutton.storage.StorageEnvFactory;
import com.scienceminer.glutton.web.resource.DataController;
import com.scienceminer.glutton.web.resource.LookupController;
import com.scienceminer.glutton.web.resource.OAController;
import com.scienceminer.glutton.web.resource.OaIstexController;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;

public class LookupServiceModule extends DropwizardAwareModule<LookupConfiguration> {

    @Override
    public void configure() {
        //REST
        bind(LookupController.class);
        bind(DataController.class);
        bind(OAController.class);
        bind(OaIstexController.class);

        //LMDB
        bind(StorageEnvFactory.class);
    }

    @Provides
    protected ObjectMapper getObjectMapper() {
        return environment().getObjectMapper();
    }

    @Provides
    protected MetricRegistry provideMetricRegistry() {
        return getMetricRegistry();
    }

    //for unit tests
    protected MetricRegistry getMetricRegistry() {
        return environment().metrics();
    }

    @Provides
    Client provideClient() {
        return ClientBuilder.newClient();
    }

}
