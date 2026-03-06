package com.scienceminer.glutton.matching;

import java.io.IOException;

/**
 * Strategy interface for pairwise matching scoring.
 * Implementations receive pre-computed features and return a match score in [0,1].
 */
public interface MatchingStrategy {

    /**
     * Compute a match score from the given features.
     *
     * @param features the computed pairwise features
     * @return a score in [0,1], with 1 indicating a perfect match
     */
    double score(MatchingFeatures features);

    /**
     * Initialize the strategy, loading any required model files.
     *
     * @param modelPath path to the directory containing model files
     * @throws IOException if model files cannot be loaded
     */
    void initialize(String modelPath) throws IOException;
}
