package com.scienceminer.lookup.storage;

import com.scienceminer.lookup.configuration.LookupConfiguration;
import org.lmdbjava.Env;
import org.lmdbjava.EnvFlags;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.nio.ByteBuffer;

@Singleton
public class StorageEnvFactory {

    private final String storagePath;
    private LookupConfiguration configuration;

    @Inject
    public StorageEnvFactory(LookupConfiguration configuration) {
        this.configuration = configuration;
        this.storagePath = configuration.getStorage();
    }

    public Env<ByteBuffer> getEnv(String envName) {
        Env<ByteBuffer> environment = null;

        File thePath = new File(this.storagePath + File.separator + envName);
        if (!thePath.exists()) {
            thePath.mkdirs();
        }

        environment = Env.create()
                .setMapSize(300L * 1024L * 1024L * 1024L)
                .setMaxReaders(configuration.getMaxAcceptedRequests())
                .setMaxDbs(10)
                .open(thePath, EnvFlags.MDB_NOTLS);

        return environment;
    }

    public LookupConfiguration getConfiguration() {
        return configuration;
    }
}
