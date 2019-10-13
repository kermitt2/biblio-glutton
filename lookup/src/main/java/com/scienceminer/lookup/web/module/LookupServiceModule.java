package com.scienceminer.lookup.web.module;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Binder;
import com.google.inject.Provides;
import com.hubspot.dropwizard.guicier.DropwizardAwareModule;
import com.scienceminer.lookup.configuration.LookupConfiguration;
import com.scienceminer.lookup.storage.StorageEnvFactory;
import com.scienceminer.lookup.web.resource.DataController;
import com.scienceminer.lookup.web.resource.LookupController;
import com.scienceminer.lookup.web.resource.OAController;
import com.scienceminer.lookup.web.resource.OaIstexController;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;

public class LookupServiceModule extends DropwizardAwareModule<LookupConfiguration> {


    @Override
    public void configure(Binder binder) {
        //REST
        binder.bind(LookupController.class);
        binder.bind(DataController.class);
        binder.bind(OAController.class);
        binder.bind(OaIstexController.class);

        //LMDB
        binder.bind(StorageEnvFactory.class);
    }

    @Provides
    protected ObjectMapper getObjectMapper() {
        return getEnvironment().getObjectMapper();
    }

    @Provides
    protected MetricRegistry provideMetricRegistry() {
        return getMetricRegistry();
    }

    //for unit tests
    protected MetricRegistry getMetricRegistry() {
        return getEnvironment().metrics();
    }

    @Provides
    Client provideClient() {
        return ClientBuilder.newClient();
    }

}
