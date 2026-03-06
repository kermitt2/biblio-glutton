package com.scienceminer.glutton.matching;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OrtSession.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

/**
 * Neural network matching strategy using a small MLP via ONNX Runtime.
 * Architecture: input(8) -> hidden(32) -> hidden(16) -> output(1) + sigmoid
 *
 * Requires com.microsoft.onnxruntime:onnxruntime on the classpath.
 */
public class NeuralStrategy implements MatchingStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeuralStrategy.class);

    private static final String MODEL_FILE = "neural_matching.onnx";

    private OrtEnvironment env;
    private OrtSession session;
    private String inputName;

    @Override
    public double score(MatchingFeatures features) {
        try {
            float[][] inputData = new float[1][MatchingFeatures.NUM_FEATURES];
            boolean[] present = features.getFeaturePresent();
            double[] values = features.getFeatures();

            for (int i = 0; i < MatchingFeatures.NUM_FEATURES; i++) {
                if (present[i]) {
                    inputData[0][i] = (float) values[i];
                } else {
                    inputData[0][i] = 0.0f; // Use 0 for missing features in neural net
                }
            }

            OnnxTensor inputTensor = OnnxTensor.createTensor(env, inputData);
            Result result = session.run(Collections.singletonMap(inputName, inputTensor));

            float[][] output = (float[][]) result.get(0).getValue();
            float score = output[0][0];

            inputTensor.close();
            result.close();

            // Clamp to [0,1] in case the model output isn't properly bounded
            return Math.max(0.0, Math.min(1.0, score));
        } catch (OrtException e) {
            LOGGER.error("ONNX Runtime prediction error, falling back to 0.0", e);
            return 0.0;
        }
    }

    @Override
    public void initialize(String modelPath) throws IOException {
        File modelFile = new File(modelPath, MODEL_FILE);
        if (!modelFile.exists()) {
            throw new IOException("Neural model file not found: " + modelFile.getAbsolutePath());
        }

        try {
            env = OrtEnvironment.getEnvironment();
            session = env.createSession(modelFile.getAbsolutePath());
            inputName = session.getInputNames().iterator().next();
            LOGGER.info("Loaded ONNX neural model from {}", modelFile.getAbsolutePath());
        } catch (OrtException e) {
            throw new IOException("Failed to load ONNX model: " + e.getMessage(), e);
        }
    }
}
