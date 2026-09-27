package com.scienceminer.glutton.command;

import com.scienceminer.glutton.configuration.LookupConfiguration;
import com.scienceminer.glutton.storage.StorageEnvFactory;
import com.scienceminer.glutton.storage.lookup.PMIdsLookup;
import com.scienceminer.glutton.utils.io.DataSource;
import com.scienceminer.glutton.utils.io.InputLocation;
import com.scienceminer.glutton.utils.pubmed.PubMedRecords;
import io.dropwizard.core.cli.ConfiguredCommand;
import io.dropwizard.core.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPOutputStream;

/**
 * Converts the MEDLINE/PubMed files into a dump in the Crossref JSON format, one JSON lines file
 * per input file. Nothing is stored: this is for using the records somewhere else.
 */
public class PubMedDumpCommand extends ConfiguredCommand<LookupConfiguration> {
    private static final Logger LOGGER = LoggerFactory.getLogger(PubMedDumpCommand.class);

    public static final String SOURCE = "pubmedDumpSource";
    public static final String OUTPUT = "pubmedDumpOutput";
    public static final String THREADS = "pubmedDumpThreads";

    public PubMedDumpCommand() {
        super("pubmed_dump", "Convert the MEDLINE/PubMed files into a dump in the Crossref JSON format");
    }

    @Override
    public void configure(Subparser subparser) {
        super.configure(subparser);

        subparser.addArgument("--input")
                .dest(SOURCE)
                .type(String.class)
                .required(true)
                .help("Location of the MEDLINE/PubMed files (pubmed*.xml.gz): a local file, a "
                        + "local directory, or an s3:// location");

        subparser.addArgument("--output")
                .dest(OUTPUT)
                .type(String.class)
                .required(true)
                .help("Directory to write the dump to, one .json.gz file per input file");

        subparser.addArgument("--threads")
                .dest(THREADS)
                .type(Integer.class)
                .required(false)
                .help("Number of files converted in parallel. Defaults to "
                        + LoadPubMedCommand.defaultThreads() + " on this machine.");
    }

    @Override
    protected void run(Bootstrap bootstrap, Namespace namespace, LookupConfiguration configuration)
            throws Exception {
        final Integer givenThreads = namespace.getInt(THREADS);
        final int threads = (givenThreads == null)
                ? LoadPubMedCommand.defaultThreads() : Math.max(1, givenThreads);

        Path output = Paths.get(namespace.getString(OUTPUT));
        Files.createDirectories(output);

        StorageEnvFactory storageEnvFactory = new StorageEnvFactory(configuration);
        PubMedRecords converter = new PubMedRecords(PMIdsLookup.getInstance(storageEnvFactory));

        long start = System.nanoTime();
        int failedFiles;
        long records;
        try (InputLocation location = InputLocation.open(namespace.getString(SOURCE),
                configuration.getS3(), PubMedRecords.FILE_SUFFIXES)) {
            List<DataSource> sources = location.getSources();
            LOGGER.info("About to convert " + sources.size() + " file(s) with " + threads + " thread(s)");

            AtomicLong written = new AtomicLong();
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            List<Future<?>> tasks = new ArrayList<>();
            for (DataSource source : sources) {
                tasks.add(pool.submit(() -> {
                    long count = dump(source, converter, output);
                    LOGGER.info("Wrote " + count + " record(s) of " + source.name());
                    written.addAndGet(count);
                    return null;
                }));
            }
            pool.shutdown();

            failedFiles = 0;
            for (Future<?> task : tasks) {
                try {
                    task.get();
                } catch (Exception e) {
                    failedFiles++;
                    LOGGER.error("Failed to convert one of the files", (e.getCause() == null) ? e : e.getCause());
                }
            }
            records = written.get();
        }

        LOGGER.info("Total PMID entries parsed, converted and written: " + records + ", in "
                + TimeUnit.SECONDS.convert(System.nanoTime() - start, TimeUnit.NANOSECONDS) + " s");
        if (failedFiles > 0) {
            throw new IllegalStateException(failedFiles + " file(s) could not be converted, the "
                    + "dump is incomplete. See the errors above.");
        }
    }

    /**
     * Writes the records of one file. The file is written under another name and only takes its
     * own once complete, so that a run that was interrupted leaves no file that looks finished.
     *
     * @return the number of records written
     */
    static long dump(DataSource source, PubMedRecords converter, Path outputDirectory) throws IOException {
        Path target = outputDirectory.resolve(PubMedRecords.baseName(source.name()) + ".json.gz");
        Path partial = outputDirectory.resolve(target.getFileName() + ".part");
        final long[] count = { 0 };
        try (Writer writer = new BufferedWriter(new OutputStreamWriter(
                new GZIPOutputStream(Files.newOutputStream(partial)), StandardCharsets.UTF_8))) {
            converter.convert(source, (pmid, json) -> {
                try {
                    // a record is one line, whatever line breaks its abstract came with
                    writer.write(json.replace('\n', ' ').replace('\r', ' '));
                    writer.write('\n');
                    count[0]++;
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            });
        } catch (java.io.UncheckedIOException e) {
            Files.deleteIfExists(partial);
            throw new IOException("Could not write " + target, e.getCause());
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(partial);
            throw new IOException("Could not convert " + source.name(), e);
        }
        Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
        return count[0];
    }
}
