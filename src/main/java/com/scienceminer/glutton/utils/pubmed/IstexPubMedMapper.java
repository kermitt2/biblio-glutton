package com.scienceminer.glutton.utils.pubmed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scienceminer.glutton.data.IstexData;
import com.scienceminer.glutton.data.PmidData;
import com.scienceminer.glutton.storage.lookup.PMIdsLookup;
import com.scienceminer.glutton.storage.lookup.PubMedLookup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Gives an ISTEX record its PMID and PMC ID, found by its DOI in the PubMed mapping, and on
 * request the MeSH classes of the PubMed record.
 */
public class IstexPubMedMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(IstexPubMedMapper.class);

    private final PMIdsLookup pmidLookup;
    private final PubMedLookup pubMedLookup;
    private final boolean addMesh;
    private final ObjectMapper mapper = new ObjectMapper();

    // what is reported at the end
    private long seen;
    private long pmidFound;
    private long pmcFound;
    private long pmidAlreadyThere;
    private long pmidOnlyInIstex;
    private long conflicts;
    private long withMesh;
    private long meshClasses;
    private long written;

    /**
     * @param pubMedLookup the PubMed records, needed only with {@code addMesh}
     */
    public IstexPubMedMapper(PMIdsLookup pmidLookup, PubMedLookup pubMedLookup, boolean addMesh) {
        this.pmidLookup = pmidLookup;
        this.pubMedLookup = pubMedLookup;
        this.addMesh = addMesh;
    }

    /**
     * @return the record completed, as one line of JSON, or null when no PMID is known for it:
     *         only the ISTEX records that are in PubMed make the mapping
     */
    public String map(IstexData istex) throws java.io.IOException {
        seen++;
        String doi = first(istex.getDoi());
        String existingPmid = first(istex.getPmid());
        if ("0".equals(existingPmid)) {
            existingPmid = null;
        }
        if (existingPmid != null) {
            pmidAlreadyThere++;
        }

        String pmid = null;
        String pmc = null;
        if (doi != null) {
            PmidData ids = pmidLookup.retrieveIdsByDoi(doi);
            if (ids != null) {
                if (isNotBlank(ids.getPmid())) {
                    pmid = ids.getPmid();
                    pmidFound++;
                }
                if (isNotBlank(ids.getPmcid())) {
                    pmc = ids.getPmcid();
                    pmcFound++;
                }
            }
        }

        if (existingPmid != null) {
            if (pmid == null) {
                pmidOnlyInIstex++;
            } else if (!pmid.equals(existingPmid)) {
                LOGGER.warn("Conflicting PMID for the ISTEX record " + istex.getIstexId() + ": "
                        + existingPmid + " in ISTEX, " + pmid + " by its DOI " + doi);
                conflicts++;
            }
            // the PMID ISTEX gives wins
            pmid = existingPmid;
        }
        if (pmid == null) {
            return null;
        }

        ObjectNode json = mapper.createObjectNode();
        json.put("corpusName", istex.getCorpusName());
        json.put("istexId", istex.getIstexId());
        array(json, "doi", doi);
        array(json, "ark", first(istex.getArk()));
        array(json, "pmid", pmid);
        array(json, "pmc", pmc);
        array(json, "pii", first(istex.getPii()));

        ArrayNode mesh = json.putArray("mesh");
        if (addMesh) {
            String record = pubMedLookup.retrieveJsonDocument(pmid);
            if (record != null) {
                JsonNode classes = mapper.readTree(record).path("mesh");
                if (classes.isArray() && classes.size() > 0) {
                    mesh.addAll((ArrayNode) classes);
                    withMesh++;
                    meshClasses += classes.size();
                }
            }
        }
        written++;
        return mapper.writeValueAsString(json);
    }

    private static void array(ObjectNode json, String field, String value) {
        ArrayNode array = json.putArray(field);
        if (value != null) {
            array.add(value);
        }
    }

    private static String first(List<String> values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (isNotBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    public long getWritten() {
        return written;
    }

    public String report() {
        StringBuilder report = new StringBuilder();
        report.append("ISTEX records considered: ").append(seen);
        report.append("\nPMID found by DOI: ").append(pmidFound);
        report.append("\nPMC ID found by DOI: ").append(pmcFound);
        report.append("\nPMID already in the ISTEX record: ").append(pmidAlreadyThere);
        report.append("\nPMID in the ISTEX record but not in the mapping: ").append(pmidOnlyInIstex);
        report.append("\nConflicts between the two: ").append(conflicts);
        report.append("\nISTEX records written, those with a PMID: ").append(written);
        if (addMesh) {
            report.append("\nISTEX records with at least one MeSH class: ").append(withMesh);
            report.append("\nMeSH classes added: ").append(meshClasses);
            if (withMesh > 0) {
                report.append("\nMeSH classes per record that has some: ")
                        .append(String.format(java.util.Locale.ROOT, "%.1f", (double) meshClasses / withMesh));
            }
        }
        return report.toString();
    }
}
