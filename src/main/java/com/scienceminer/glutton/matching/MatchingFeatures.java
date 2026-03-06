package com.scienceminer.glutton.matching;

import com.rockymadden.stringmetric.similarity.RatcliffObershelpMetric;
import com.scienceminer.glutton.data.MatchingDocument;
import org.apache.commons.lang3.StringUtils;
import scala.Option;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Extracts pairwise matching features between a candidate document and a reference document.
 * Features are stored as a double[] with a parallel boolean[] tracking which features are present.
 *
 * Feature indices:
 * [0] titleSim      - Ratcliff-Obershelp similarity on article title
 * [1] authorSim     - Ratcliff-Obershelp similarity on first author
 * [2] blockingScore  - ES blocking/retrieval score (normalized)
 * [3] jtitleSim     - Ratcliff-Obershelp similarity on journal title
 * [4] yearMatch     - Exact year match (1.0 or 0.0)
 * [5] volumeMatch   - Exact volume match (1.0 or 0.0)
 * [6] issueMatch    - Exact issue match (1.0 or 0.0)
 * [7] firstPageMatch - Exact first page match (1.0 or 0.0)
 */
public class MatchingFeatures {

    public static final int NUM_FEATURES = 8;

    public static final int TITLE_SIM = 0;
    public static final int AUTHOR_SIM = 1;
    public static final int BLOCKING_SCORE = 2;
    public static final int JTITLE_SIM = 3;
    public static final int YEAR_MATCH = 4;
    public static final int VOLUME_MATCH = 5;
    public static final int ISSUE_MATCH = 6;
    public static final int FIRST_PAGE_MATCH = 7;

    public static final String[] FEATURE_NAMES = {
        "titleSim", "authorSim", "blockingScore", "jtitleSim",
        "yearMatch", "volumeMatch", "issueMatch", "firstPageMatch"
    };

    private final double[] features;
    private final boolean[] featurePresent;

    public MatchingFeatures(double[] features, boolean[] featurePresent) {
        this.features = features;
        this.featurePresent = featurePresent;
    }

    public double[] getFeatures() {
        return features;
    }

    public boolean[] getFeaturePresent() {
        return featurePresent;
    }

    public double getFeature(int index) {
        return features[index];
    }

    public boolean isPresent(int index) {
        return featurePresent[index];
    }

    /**
     * Compute pairwise matching features between a candidate and a reference document.
     *
     * @param candidate the candidate matching document (from ES blocking)
     * @param reference the reference document with target metadata from the query
     * @return MatchingFeatures with computed feature values and presence flags
     */
    public static MatchingFeatures compute(MatchingDocument candidate, MatchingDocument reference) {
        double[] features = new double[NUM_FEATURES];
        boolean[] present = new boolean[NUM_FEATURES];

        // [0] title similarity
        if (isNotBlank(reference.getATitle())) {
            present[TITLE_SIM] = true;
            if (isNotBlank(candidate.getATitle())) {
                features[TITLE_SIM] = ratcliffObershelpDistance(reference.getATitle(), candidate.getATitle());
            }
        }

        // [1] first author similarity
        if (isNotBlank(reference.getFirstAuthor())) {
            present[AUTHOR_SIM] = true;
            if (isNotBlank(candidate.getFirstAuthor())) {
                features[AUTHOR_SIM] = ratcliffObershelpDistance(reference.getFirstAuthor(), candidate.getFirstAuthor());
            }
        }

        // [2] blocking score (always present)
        present[BLOCKING_SCORE] = true;
        features[BLOCKING_SCORE] = candidate.getBlockingScore();

        // [3] journal title similarity
        // Note: if we have a pure HAL metadata record, journal name is less reliable because of preprints
        if (isNotBlank(reference.getJTitle()) && (candidate.getDOI() != null || candidate.getPmid() != null)) {
            present[JTITLE_SIM] = true;
            double jtitleScore = 0.0;
            if (isNotBlank(candidate.getJTitle()) || isNotBlank(candidate.getAbbreviatedTitle())) {
                if (isNotBlank(candidate.getJTitle())) {
                    jtitleScore = ratcliffObershelpDistance(reference.getJTitle(), candidate.getJTitle());
                }
                if (isNotBlank(candidate.getAbbreviatedTitle())) {
                    double abbrevScore = ratcliffObershelpDistance(reference.getJTitle(), candidate.getAbbreviatedTitle());
                    if (abbrevScore > jtitleScore) {
                        jtitleScore = abbrevScore;
                    }
                }
            }
            features[JTITLE_SIM] = jtitleScore;
        }

        // [4] year match
        if (isNotBlank(reference.getYear())) {
            present[YEAR_MATCH] = true;
            if (isNotBlank(candidate.getYear()) && reference.getYear().equals(candidate.getYear())) {
                features[YEAR_MATCH] = 1.0;
            }
        }

        // [5] volume match
        if (isNotBlank(reference.getVolume())) {
            present[VOLUME_MATCH] = true;
            if (isNotBlank(candidate.getVolume()) && reference.getVolume().equals(candidate.getVolume())) {
                features[VOLUME_MATCH] = 1.0;
            }
        }

        // [6] issue match
        if (isNotBlank(reference.getIssue())) {
            present[ISSUE_MATCH] = true;
            if (isNotBlank(candidate.getIssue()) && reference.getIssue().equals(candidate.getIssue())) {
                features[ISSUE_MATCH] = 1.0;
            }
        }

        // [7] first page match
        if (isNotBlank(reference.getFirstPage())) {
            present[FIRST_PAGE_MATCH] = true;
            if (isNotBlank(candidate.getFirstPage()) && reference.getFirstPage().equals(candidate.getFirstPage())) {
                features[FIRST_PAGE_MATCH] = 1.0;
            }
        }

        return new MatchingFeatures(features, present);
    }

    /**
     * Return a CSV header line for training data export.
     */
    public static String csvHeader() {
        return String.join(",", FEATURE_NAMES) + ",label";
    }

    /**
     * Return a CSV row with feature values and a label.
     */
    public String toCsvRow(int label) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < NUM_FEATURES; i++) {
            if (i > 0) sb.append(",");
            if (featurePresent[i]) {
                sb.append(features[i]);
            } else {
                sb.append(""); // empty for missing features
            }
        }
        sb.append(",").append(label);
        return sb.toString();
    }

    private static double ratcliffObershelpDistance(String string1, String string2) {
        if (StringUtils.isBlank(string1) || StringUtils.isBlank(string2))
            return 0.0;

        string1 = string1.toLowerCase();
        string2 = string2.toLowerCase();

        if (string1.equals(string2))
            return 1.0;

        if (string1.length() > 0 && string2.length() > 0) {
            Option<Object> similarityObject = RatcliffObershelpMetric.compare(string1, string2);
            if (similarityObject != null && similarityObject.get() != null)
                return (Double) similarityObject.get();
        }

        return 0.0;
    }
}
