package com.scienceminer.glutton.matching;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * Logistic regression matching strategy with learned weights.
 * Zero external ML dependencies - pure Java implementation.
 *
 * Loads weights from a JSON file: {"bias": 0.5, "weights": [0.3, 0.25, ...]}
 * Score: sigmoid(bias + sum(wi * fi)) for present features, with reweighting for missing ones.
 */
public class LogisticRegressionStrategy implements MatchingStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(LogisticRegressionStrategy.class);

    private static final String MODEL_FILE = "logreg_weights.json";

    private double bias;
    private double[] weights;

    @Override
    public double score(MatchingFeatures features) {
        double[] values = features.getFeatures();
        boolean[] present = features.getFeaturePresent();

        // Compute weighted sum only for present features
        double sum = bias;
        double totalWeight = 0.0;
        double presentWeight = 0.0;

        for (int i = 0; i < MatchingFeatures.NUM_FEATURES; i++) {
            totalWeight += Math.abs(weights[i]);
            if (present[i]) {
                sum += weights[i] * values[i];
                presentWeight += Math.abs(weights[i]);
            }
        }

        // Reweight to account for missing features: scale the sum so that
        // the contribution of present features is proportionally adjusted
        if (presentWeight > 0 && presentWeight < totalWeight) {
            double scale = totalWeight / presentWeight;
            sum = bias + (sum - bias) * scale;
        }

        return sigmoid(sum);
    }

    @Override
    public void initialize(String modelPath) throws IOException {
        File modelFile = new File(modelPath, MODEL_FILE);
        if (!modelFile.exists()) {
            throw new IOException("Logistic regression model file not found: " + modelFile.getAbsolutePath());
        }

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(modelFile);

        bias = root.get("bias").asDouble();
        JsonNode weightsNode = root.get("weights");
        weights = new double[MatchingFeatures.NUM_FEATURES];
        for (int i = 0; i < Math.min(weightsNode.size(), MatchingFeatures.NUM_FEATURES); i++) {
            weights[i] = weightsNode.get(i).asDouble();
        }

        LOGGER.info("Loaded logistic regression model from {}", modelFile.getAbsolutePath());
    }

    private static double sigmoid(double x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }
}
