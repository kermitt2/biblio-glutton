package com.scienceminer.glutton.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * What a load has already put in the storage, so that the same command run again carries on
 * instead of starting over.
 *
 * A load of everything takes hours to days, and the machine it runs on is restarted, loses its
 * network or is put to sleep in the meantime. The storage itself cannot tell what it was loaded
 * from, so this is written down beside it, in a small text file in the directory of the database
 * the load fills: the files read to their end, how far into the file at hand the load is, or for
 * a harvest the cursor it reached. Nothing is added to the databases. The file goes away when
 * the load completes, and with the database when its directory is deleted.
 *
 * A file is recognised by its name and a fingerprint (its size, and the date or tag of its last
 * change when known), so that another file under the same name is read again.
 *
 * Something is written down only once it is safe: the loaders commit without waiting for the
 * disk, so the storage is flushed first (and the search index asked to confirm, for the loads
 * that feed it), which is what {@link Durability} does. Flushing has a cost, so it is done every
 * minute rather than at every file. What a run did after its last checkpoint is done again by the
 * next, which only writes the same records over themselves.
 */
public class LoadProgress {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoadProgress.class);

    static final long INTERVAL_MS = 60000;

    private static final String DONE = "done";
    private static final String AT = "at";
    private static final String CURSOR = "cursor";

    /** Makes what was loaded so far safe on disk; false when it cannot vouch for all of it. */
    public interface Durability {
        boolean make() throws Exception;
    }

    private final File file;
    /** name -> fingerprint of the files read to their end */
    private final Map<String, String> done = new HashMap<>();
    /** name -> fingerprint and number of records stored, for a file left part way */
    private final Map<String, String[]> positions = new HashMap<>();
    private String cursor;

    /** lines not written yet: what they say is not safe on disk until the next checkpoint */
    private final List<String> staged = new ArrayList<>();
    private final Set<String> namesOfThisRun = new HashSet<>();
    private Durability durability = () -> true;
    private boolean stopped;
    private volatile long lastCheckpoint = System.nanoTime();

    private volatile long intervalMs = INTERVAL_MS;

    private LoadProgress(File file) {
        this.file = file;
    }

    /**
     * @param directory the directory of the database the load fills
     * @param load the name of the load, as its command is called
     * @param fresh to forget what an earlier run left and load everything
     */
    public static LoadProgress open(File directory, String load, boolean fresh) throws IOException {
        Files.createDirectories(directory.toPath());
        LoadProgress progress = new LoadProgress(new File(directory, load + "-load.progress"));
        if (progress.file.exists()) {
            if (fresh) {
                Files.delete(progress.file.toPath());
                LOGGER.info("Starting over: the progress an earlier run left in " + progress.file + " is dropped");
            } else {
                progress.read();
                LOGGER.info("Carrying on from an earlier run (" + progress.describe() + ", from " + progress.file
                        + "). Add --fresh to load everything again");
            }
        }
        return progress;
    }

    private void read() throws IOException {
        String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        // a last line without its end is one the machine went down in the middle of
        int end = content.lastIndexOf('\n');
        if (end < 0) {
            return;
        }
        for (String line : content.substring(0, end).split("\n")) {
            apply(line);
        }
    }

    private void apply(String line) {
        String[] fields = line.split("\t", -1);
        if (DONE.equals(fields[0]) && fields.length == 3) {
            done.put(fields[2], fields[1]);
            positions.remove(fields[2]);
        } else if (AT.equals(fields[0]) && fields.length == 4) {
            positions.put(fields[3], new String[] { fields[2], fields[1] });
        } else if (CURSOR.equals(fields[0]) && fields.length == 2) {
            cursor = fields[1];
        }
    }

    private String describe() {
        if (cursor != null) {
            return "cursor " + cursor;
        }
        return done.size() + " file(s) read" + (positions.isEmpty() ? "" : ", " + positions.size() + " part way");
    }

    /** How long a loader goes on between two checkpoints; a minute unless a test shortens it. */
    public void setIntervalMs(long intervalMs) {
        this.intervalMs = intervalMs;
    }

    /** What makes the storage safe before progress is written down. */
    public synchronized void setDurability(Durability durability) {
        this.durability = durability;
    }

    /** Whether an earlier run left something to carry on from. */
    public synchronized boolean hasEarlierRun() {
        return !done.isEmpty() || !positions.isEmpty() || cursor != null;
    }

    /** One file of the load. */
    public synchronized Unit unit(String name, String fingerprint) {
        namesOfThisRun.add(name);
        boolean isDone = fingerprint.equals(done.get(name));
        String[] position = positions.get(name);
        long stored = 0;
        if (!isDone && position != null && fingerprint.equals(position[0])) {
            try {
                stored = Long.parseLong(position[1]);
            } catch (NumberFormatException e) {
                stored = 0;
            }
        }
        return new Unit(this, name, fingerprint, isDone, stored);
    }

    /** A file whose progress is not kept: never skipped, never written down. */
    public static Unit untracked() {
        return new Unit(null, null, null, false, 0);
    }

    /** The cursor an earlier run of a harvest reached, or null. */
    public synchronized String getCursor() {
        return cursor;
    }

    /** Everything before this cursor is stored, as far as the loader is concerned. */
    public synchronized void reached(String cursorMark) {
        stage(CURSOR + "\t" + cursorMark);
    }

    private synchronized void stage(String line) {
        if (!stopped) {
            staged.add(line);
        }
    }

    /** Whether it is time for the loader to commit what it holds and call {@link #checkpoint()}. */
    public boolean isDue() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - lastCheckpoint) >= intervalMs;
    }

    /**
     * Writes down what the loader reported since the last time, after making it safe on disk. To
     * be called with everything reported so far committed.
     */
    public synchronized void checkpoint() {
        lastCheckpoint = System.nanoTime();
        if (stopped || staged.isEmpty()) {
            return;
        }
        boolean durable;
        try {
            durable = durability.make();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            durable = false;
        } catch (Exception e) {
            LOGGER.error("Could not make the loaded records safe on disk", e);
            durable = false;
        }
        if (!durable) {
            stop("not everything loaded since the last checkpoint could be confirmed");
            return;
        }

        StringBuilder lines = new StringBuilder();
        for (String line : staged) {
            lines.append(line).append('\n');
        }
        try (FileOutputStream out = new FileOutputStream(file, true)) {
            out.write(lines.toString().getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        } catch (IOException e) {
            LOGGER.error("Could not write " + file, e);
            stop("its file could not be written");
            return;
        }
        for (String line : staged) {
            apply(line);
        }
        staged.clear();
        lastCheckpoint = System.nanoTime();
    }

    /**
     * Nothing more is written down by this run: what it loads from here on is loaded again by the
     * next. For a run that cannot tell any more whether what it loaded is all there.
     */
    public synchronized void stop(String why) {
        if (!stopped) {
            stopped = true;
            staged.clear();
            LOGGER.warn("The progress of this load is no longer recorded: " + why
                    + ". The next run carries on from the last checkpoint (" + describe() + ")");
        }
    }

    /**
     * Ends the run. A complete load has nothing to carry on from, so its file is removed; unless
     * it holds files this run was not about (a folder loaded on its own while the load of the
     * whole is unfinished), which the load of the whole still needs.
     */
    public synchronized void finish(boolean complete) {
        checkpoint();
        if (!complete || stopped) {
            if (file.exists()) {
                LOGGER.info("The load is not complete. Run the same command again to carry on from where it "
                        + "stopped (" + describe() + ")");
            }
            return;
        }
        Set<String> others = new HashSet<>(done.keySet());
        others.addAll(positions.keySet());
        others.removeAll(namesOfThisRun);
        if (!others.isEmpty()) {
            LOGGER.info("Keeping " + file + ": it holds " + others.size() + " file(s) of another, unfinished load");
            return;
        }
        try {
            Files.deleteIfExists(file.toPath());
        } catch (IOException e) {
            LOGGER.warn("Could not delete " + file + ": delete it by hand, or the next load skips what it lists", e);
        }
    }

    File getFile() {
        return file;
    }

    /**
     * One file of a load: whether an earlier run read it, how many of its records an earlier run
     * stored, and how many this run has.
     */
    public static final class Unit {

        private final LoadProgress progress;
        private final String name;
        private final String fingerprint;
        private final boolean done;
        private final long toSkip;
        private long records;

        private Unit(LoadProgress progress, String name, String fingerprint, boolean done, long toSkip) {
            this.progress = progress;
            this.name = name;
            this.fingerprint = fingerprint;
            this.done = done;
            this.toSkip = toSkip;
        }

        /** Whether an earlier run read this file to its end. */
        public boolean isDone() {
            return done;
        }

        /** How many records of this file an earlier run stored, which this one passes over. */
        public long getStoredEarlier() {
            return toSkip;
        }

        /**
         * To be asked for every record, in the order of the file: true for the ones an earlier
         * run stored, which are then to be left alone.
         */
        public boolean skip() {
            if (records < toSkip) {
                records++;
                return true;
            }
            return false;
        }

        /** One more record of this file is stored. */
        public void stored() {
            records++;
        }

        public boolean isDue() {
            return progress != null && progress.isDue();
        }

        /**
         * Writes down how far into the file the load is. To be called with every record counted
         * by {@link #stored()} committed.
         */
        public void checkpoint() {
            if (progress != null) {
                progress.stage(AT + "\t" + records + "\t" + fingerprint + "\t" + name);
                progress.checkpoint();
            }
        }

        /**
         * The file was read to its end. Written down at the next {@link LoadProgress#checkpoint()},
         * which is to be called with every record of the file committed.
         */
        public void finished() {
            if (progress != null) {
                progress.stage(DONE + "\t" + fingerprint + "\t" + name);
            }
        }
    }
}
