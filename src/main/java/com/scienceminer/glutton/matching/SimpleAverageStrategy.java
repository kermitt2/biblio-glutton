package com.scienceminer.glutton.matching;

import java.io.IOException;

/**
 * Default matching strategy that reimplements the original recordDistance() logic:
 * simple arithmetic mean of the original 5 features (title, author, blocking, jtitle, year).
 * Volume, issue, and firstPage are excluded to preserve exact backward compatibility.
 */
public class SimpleAverageStrategy implements MatchingStrategy {

    // Only use the original 5 features that were active in the pre-strategy code
    private static final int[] ACTIVE_FEATURES = {
        MatchingFeatures.TITLE_SIM,
        MatchingFeatures.AUTHOR_SIM,
        MatchingFeatures.BLOCKING_SCORE,
        MatchingFeatures.JTITLE_SIM,
        MatchingFeatures.YEAR_MATCH
    };

    @Override
    public double score(MatchingFeatures features) {
        int nbCriteria = 0;
        double accumulatedScore = 0.0;

        double[] values = features.getFeatures();
        boolean[] present = features.getFeaturePresent();

        for (int idx : ACTIVE_FEATURES) {
            if (present[idx]) {
                nbCriteria++;
                accumulatedScore += values[idx];
            }
        }

        if (nbCriteria == 0)
            return 0.0;

        return accumulatedScore / nbCriteria;
    }

    @Override
    public void initialize(String modelPath) throws IOException {
        // No-op: no model to load for simple average
    }
}
