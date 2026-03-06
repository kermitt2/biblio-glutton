package com.scienceminer.glutton.command;

import com.scienceminer.glutton.configuration.LookupConfiguration;
import com.scienceminer.glutton.storage.StorageEnvFactory;
import com.scienceminer.glutton.storage.lookup.CrossrefMetadataLookup;
import com.scienceminer.glutton.utils.BinarySerialiser;
import com.scienceminer.glutton.utils.Compressors;
import com.scienceminer.glutton.utils.CompressionType;
import io.dropwizard.core.cli.ConfiguredCommand;
import io.dropwizard.core.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Command for benchmarking different compression algorithms on existing Crossref LMDB data.
 * Reads sample records and measures compression ratio, compress time, and decompress time
 * for each supported algorithm.
 */
public class CompressionBenchmarkCommand extends ConfiguredCommand<LookupConfiguration> {
    private static final Logger LOGGER = LoggerFactory.getLogger(CompressionBenchmarkCommand.class);

    private static final int DEFAULT_SAMPLE_SIZE = 1000;

    public CompressionBenchmarkCommand() {
        super("compression_benchmark", "Benchmark compression algorithms on existing Crossref LMDB data");
    }

    @Override
    public void configure(Subparser subparser) {
        super.configure(subparser);
        subparser.addArgument("-n", "--num-samples")
                .dest("numSamples")
                .type(Integer.class)
                .setDefault(DEFAULT_SAMPLE_SIZE)
                .help("Number of sample records to benchmark (default: " + DEFAULT_SAMPLE_SIZE + ")");
    }

    @Override
    protected void run(Bootstrap bootstrap, Namespace namespace, LookupConfiguration configuration) throws Exception {
        int numSamples = namespace.getInt("numSamples");

        StorageEnvFactory storageEnvFactory = new StorageEnvFactory(configuration);
        CrossrefMetadataLookup crossrefLookup = CrossrefMetadataLookup.getInstance(storageEnvFactory);

        LOGGER.info("Loading {} sample records from Crossref LMDB...", numSamples);
        List<Pair<String, String>> records = crossrefLookup.retrieveList(numSamples);

        if (records.isEmpty()) {
            LOGGER.error("No records found in Crossref LMDB. Load data first with ./gradlew crossref");
            System.exit(1);
        }

        LOGGER.info("Loaded {} records. Running compression benchmarks...", records.size());

        // Collect raw serialized data (FST-serialized, not compressed)
        byte[][] rawData = new byte[records.size()][];
        long totalRawSize = 0;
        for (int i = 0; i < records.size(); i++) {
            rawData[i] = BinarySerialiser.serialize(records.get(i).getValue());
            totalRawSize += rawData[i].length;
        }

        CompressionType[] types = CompressionType.values();

        System.out.println();
        System.out.printf("Compression Benchmark Results (%d records, %.2f MB raw FST-serialized data)%n",
                records.size(), totalRawSize / (1024.0 * 1024.0));
        System.out.println("=".repeat(100));
        System.out.printf("%-10s %15s %15s %15s %18s %18s%n",
                "Algorithm", "Raw Size (MB)", "Comp Size (MB)", "Ratio", "Compress (ms)", "Decompress (ms)");
        System.out.println("-".repeat(100));

        for (CompressionType type : types) {
            long compressedSize = 0;
            long compressTimeNs = 0;
            long decompressTimeNs = 0;

            try {
                byte[][] compressed = new byte[rawData.length][];

                // Compress
                long startCompress = System.nanoTime();
                for (int i = 0; i < rawData.length; i++) {
                    compressed[i] = Compressors.compress(rawData[i], type);
                    compressedSize += compressed[i].length;
                }
                compressTimeNs = System.nanoTime() - startCompress;

                // Decompress
                long startDecompress = System.nanoTime();
                for (int i = 0; i < compressed.length; i++) {
                    Compressors.decompress(compressed[i], type);
                }
                decompressTimeNs = System.nanoTime() - startDecompress;

                double ratio = (double) compressedSize / totalRawSize;
                double compressTimeMs = compressTimeNs / 1_000_000.0;
                double decompressTimeMs = decompressTimeNs / 1_000_000.0;

                System.out.printf("%-10s %15.2f %15.2f %14.3f %17.1f %17.1f%n",
                        type.name(),
                        totalRawSize / (1024.0 * 1024.0),
                        compressedSize / (1024.0 * 1024.0),
                        ratio,
                        compressTimeMs,
                        decompressTimeMs);
            } catch (Exception e) {
                System.out.printf("%-10s %s%n", type.name(), "ERROR: " + e.getMessage());
                LOGGER.error("Benchmark failed for " + type.name(), e);
            }
        }

        System.out.println("=".repeat(100));
        System.out.println();

        System.exit(0);
    }
}
