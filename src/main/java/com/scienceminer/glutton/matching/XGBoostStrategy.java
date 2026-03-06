package com.scienceminer.glutton.matching;

import ml.dmlc.xgboost4j.java.Booster;
import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.XGBoost;
import ml.dmlc.xgboost4j.java.XGBoostError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * XGBoost gradient boosted trees matching strategy.
 * Uses xgboost4j for inference. Handles missing features natively via NaN values.
 *
 * Requires ml.dmlc:xgboost4j_2.12 on the classpath.
 */
public class XGBoostStrategy implements MatchingStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(XGBoostStrategy.class);

    private static final String MODEL_FILE = "xgboost_matching.model";

    private Booster booster;

    @Override
    public double score(MatchingFeatures features) {
        try {
            float[] featureValues = new float[MatchingFeatures.NUM_FEATURES];
            boolean[] present = features.getFeaturePresent();
            double[] values = features.getFeatures();

            for (int i = 0; i < MatchingFeatures.NUM_FEATURES; i++) {
                if (present[i]) {
                    featureValues[i] = (float) values[i];
                } else {
                    featureValues[i] = Float.NaN; // XGBoost handles NaN as missing
                }
            }

            DMatrix dMatrix = new DMatrix(featureValues, 1, MatchingFeatures.NUM_FEATURES, Float.NaN);
            float[][] predictions = booster.predict(dMatrix);
            dMatrix.dispose();

            return predictions[0][0];
        } catch (XGBoostError e) {
            LOGGER.error("XGBoost prediction error, falling back to 0.0", e);
            return 0.0;
        }
    }

    @Override
    public void initialize(String modelPath) throws IOException {
        File modelFile = new File(modelPath, MODEL_FILE);
        if (!modelFile.exists()) {
            throw new IOException("XGBoost model file not found: " + modelFile.getAbsolutePath());
        }

        try {
            booster = XGBoost.loadModel(modelFile.getAbsolutePath());
            LOGGER.info("Loaded XGBoost model from {}", modelFile.getAbsolutePath());
        } catch (XGBoostError e) {
            throw new IOException("Failed to load XGBoost model: " + e.getMessage(), e);
        }
    }
}
