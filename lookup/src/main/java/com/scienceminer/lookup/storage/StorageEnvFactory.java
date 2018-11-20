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
    private Env<ByteBuffer> environment = null;

    @Inject
    public StorageEnvFactory(LookupConfiguration configuration) {
        this.configuration = configuration;
        this.storagePath = configuration.getStorage();
    }

    public Env<ByteBuffer> getEnv() {

        if (environment != null) {
            return environment;
        }

        File thePath = new File(this.storagePath);
        if (!thePath.exists()) {
            thePath.mkdirs();
        }

        this.environment = Env.create()
                .setMapSize(300L * 1024L * 1024L * 1024L)
                .setMaxReaders(126)
                .setMaxDbs(10)
                .open(thePath, EnvFlags.MDB_NOTLS);

        return environment;

    }

    public LookupConfiguration getConfiguration() {
        return configuration;
    }
}
