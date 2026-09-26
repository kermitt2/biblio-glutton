package com.scienceminer.glutton.storage;

import com.scienceminer.glutton.configuration.LookupConfiguration;
import org.lmdbjava.Env;
import org.lmdbjava.EnvFlags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class StorageEnvFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(StorageEnvFactory.class);

    private final String storagePath;
    private LookupConfiguration configuration;
    private final boolean bulkLoad;
    private final List<Env<ByteBuffer>> opened = new ArrayList<>();

    @Inject
    public StorageEnvFactory(LookupConfiguration configuration) {
        this(configuration, false);
    }

    /**
     * With {@code bulkLoad}, the environments are opened for loading a database from a file
     * rather than serving it: a commit no longer waits for the disk. LMDB otherwise flushes
     * every page a transaction touched before the commit returns, and a load whose keys arrive
     * in no order (DOIs, say) touches pages all over the tree once it is bigger than memory,
     * so each commit turns into a burst of random synchronous writes and the rate keeps falling
     * as the tree grows. Without the flush the operating system writes the pages back in its
     * own time and order. The price is durability against a crash of the machine during the
     * load, which a database rebuilt from a download does not need; a crash of the process
     * alone loses nothing, the pages are the kernel's. Every environment is flushed when the
     * JVM exits, and the flags are not stored in the file, so the service opens the result as
     * any other database.
     */
    public StorageEnvFactory(LookupConfiguration configuration, boolean bulkLoad) {
        this.configuration = configuration;
        this.storagePath = configuration.getStorage();
        this.bulkLoad = bulkLoad;
        if (bulkLoad) {
            LOGGER.info("Opening the storage for bulk loading: commits do not wait for the disk");
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    syncAll();
                } catch (RuntimeException e) {
                    LOGGER.warn("Could not flush an LMDB environment to disk", e);
                }
            }, "lmdb-sync"));
        }
    }

    public Env<ByteBuffer> getEnv(String envName) {
        Env<ByteBuffer> environment = null;

        File thePath = new File(this.storagePath + File.separator + envName);
        if (!thePath.exists()) {
            thePath.mkdirs();
        }

        EnvFlags[] flags = bulkLoad
                ? new EnvFlags[] { EnvFlags.MDB_NOTLS, EnvFlags.MDB_NOSYNC, EnvFlags.MDB_NOMETASYNC }
                : new EnvFlags[] { EnvFlags.MDB_NOTLS };

        environment = Env.create()
                .setMapSize(1024L * 1024L * 1024L * 1024L)
                .setMaxReaders(configuration.getMaxAcceptedRequests())
                .setMaxDbs(10)
                .open(thePath, flags);

        synchronized (opened) {
            opened.add(environment);
        }
        return environment;
    }

    /**
     * Flushes every environment opened here to disk. A no-op for one that was already closed,
     * and cheap for one with nothing left to write. A loading command calls it once it is done,
     * so that a flush that fails makes the command fail rather than leave a database that looks
     * complete but is not on disk; the JVM exit flushes again, as a fallback for a load that ends
     * some other way.
     *
     * @throws RuntimeException the failure of the first environment that could not be flushed,
     *         after the others were tried
     */
    public void syncAll() {
        List<Env<ByteBuffer>> envs;
        synchronized (opened) {
            envs = new ArrayList<>(opened);
        }
        RuntimeException failure = null;
        for (Env<ByteBuffer> env : envs) {
            try {
                if (!env.isClosed()) {
                    env.sync(true);
                }
            } catch (RuntimeException e) {
                if (failure == null) {
                    failure = e;
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    public boolean isBulkLoad() {
        return bulkLoad;
    }

    public LookupConfiguration getConfiguration() {
        return configuration;
    }
}
