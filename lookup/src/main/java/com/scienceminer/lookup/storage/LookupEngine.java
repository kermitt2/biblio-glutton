package com.scienceminer.lookup.storage;

import com.rockymadden.stringmetric.similarity.RatcliffObershelpMetric;
import com.scienceminer.lookup.data.IstexData;
import com.scienceminer.lookup.data.MatchingDocument;
import com.scienceminer.lookup.data.PmidData;
import com.scienceminer.lookup.exception.NotFoundException;
import com.scienceminer.lookup.exception.ServiceException;
import com.scienceminer.lookup.storage.lookup.IstexIdsLookup;
import com.scienceminer.lookup.storage.lookup.MetadataLookup;
import com.scienceminer.lookup.storage.lookup.OALookup;
import com.scienceminer.lookup.storage.lookup.PMIdsLookup;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.omg.CORBA.SystemException;
import org.omg.CosNaming.NamingContextPackage.NotFound;
import scala.Option;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.length;

public class LookupEngine {

    private OALookup oaDoiLookup = null;
    private IstexIdsLookup istexLookup = null;
    private MetadataLookup metadataLookup = null;
    
    private PMIdsLookup pmidLookup = null;

    public static Pattern DOIPattern = Pattern.compile("\"DOI\"\\s?:\\s?\"(10\\.\\d{4,5}\\/[^\"\\s]+[^;,.\\s])\"");

    public LookupEngine() {
    }

    public LookupEngine(StorageEnvFactory storageFactory) {
        this.oaDoiLookup = new OALookup(storageFactory);
        this.istexLookup = new IstexIdsLookup(storageFactory);
        this.metadataLookup = new MetadataLookup(storageFactory);
        this.pmidLookup = new PMIdsLookup(storageFactory);
    }


    public String retrieveByArticleMetadata(String title, String firstAuthor, Boolean postValidate) {
        MatchingDocument outputData = metadataLookup.retrieveByMetadata(title, firstAuthor);
        if (postValidate != null && postValidate) {
            if (!areMetadataMatching(title, firstAuthor, outputData)) {
                throw new NotFoundException("Article found but it didn't passed the post Validation.");
            }
        }
        return injectIdsByDoi(outputData.getJsonObject(), outputData.getDOI());
    }

    public String retrieveByJournalMetadata(String title, String volume, String firstPage) {
        MatchingDocument outputData = metadataLookup.retrieveByMetadata(title, volume, firstPage);
        return injectIdsByDoi(outputData.getJsonObject(), outputData.getDOI());
    }

    public String retrieveByJournalMetadata(String title, String volume, String firstPage, String firstAuthor) {
        MatchingDocument outputData = metadataLookup.retrieveByMetadata(title, volume, firstPage, firstAuthor);
        return injectIdsByDoi(outputData.getJsonObject(), outputData.getDOI());
    }

    public String retrieveByDoi(String doi) {
        MatchingDocument outputData = metadataLookup.retrieveByMetadata(doi);
        return injectIdsByDoi(outputData.getJsonObject(), outputData.getDOI());
    }

    public String retrieveByPmid(String pmid) {
        final PmidData pmidData = pmidLookup.retrieveIdsByPmid(pmid);

        if (pmidData != null && isNotBlank(pmidData.getDoi())) {
            return retrieveByDoi(pmidData.getDoi());
        }

        throw new NotFoundException("Cannot find record by PMID " + pmid);
    }

    public String retrieveByPmc(String pmc) {
        final PmidData pmidData = pmidLookup.retrieveIdsByPmc(pmc);

        if (pmidData != null && isNotBlank(pmidData.getDoi())) {
            return retrieveByDoi(pmidData.getDoi());
        }

        throw new NotFoundException("Cannot find record by PMC ID " + pmc);
    }

    public String retrieveByIstexid(String istexid) {
        final IstexData istexData = istexLookup.retrieveByIstexId(istexid);


        if (istexData != null && CollectionUtils.isNotEmpty(istexData.getDoi()) && isNotBlank(istexData.getDoi().get(0))) {
            final String doi = istexData.getDoi().get(0);
            MatchingDocument outputData = metadataLookup.retrieveByMetadata(doi);
            return injectIdsByIstexData(outputData.getJsonObject(), doi, istexData);
        }

        throw new NotFoundException("Cannot find record by Istex ID " + istexid);
    }

    // Intermediate lookups
    public PmidData retrievePMidsByDoi(String doi) {
        return pmidLookup.retrieveIdsByDoi(doi);
    }

    public PmidData retrievePMidsByPmid(String pmid) {
        return pmidLookup.retrieveIdsByPmid(pmid);
    }

    public PmidData retrievePMidsByPmc(String pmc) {
        return pmidLookup.retrieveIdsByPmc(pmc);
    }

    public IstexData retrieveIstexIdsByDoi(String doi) {
        return istexLookup.retrieveByDoi(doi);
    }

    public IstexData retrieveIstexIdsByIstexId(String istexId) {
        return istexLookup.retrieveByIstexId(istexId);
    }

    public String retrieveOAUrlByDoi(String doi) {

        return oaDoiLookup.retrieveOALinkByDoi(doi);
    }

    public String retrieveOAUrlByPmid(String pmid) {
        final PmidData pmidData = pmidLookup.retrieveIdsByPmid(pmid);

        if (pmidData != null && isNotBlank(pmidData.getDoi())) {
            return oaDoiLookup.retrieveOALinkByDoi(pmidData.getDoi());
        }

        throw new NotFoundException("Open Access URL was not found for PM ID " + pmid);
    }

    public String retrieveOAUrlByPmc(String pmc) {
        final PmidData pmidData = pmidLookup.retrieveIdsByPmc(pmc);

        if (pmidData != null && isNotBlank(pmidData.getDoi())) {
            return oaDoiLookup.retrieveOALinkByDoi(pmidData.getDoi());
        }

        throw new NotFoundException("Open Access URL was not found for PM ID " + pmc);
    }

    public String retrieveByBiblio(String biblio) {
        final MatchingDocument outputData = metadataLookup.retrieveByBiblio(biblio);
        return injectIdsByDoi(outputData.getJsonObject(), outputData.getDOI());
    }

    protected void setOaDoiLookup(OALookup oaDoiLookup) {
        this.oaDoiLookup = oaDoiLookup;
    }

    protected void setIstexLookup(IstexIdsLookup istexLookup) {
        this.istexLookup = istexLookup;
    }

    protected void setMetadataLookup(MetadataLookup metadataLookup) {
        this.metadataLookup = metadataLookup;
    }

    protected void setPmidLookup(PMIdsLookup pmidLookup) {
        this.pmidLookup = pmidLookup;
    }

    public String fetchDOI(String input) {
        //From grobid
//        Pattern DOIPattern = Pattern.compile("(10\\.\\d{4,5}\\/[\\S]+[^;,.\\s])");

        Matcher doiMatcher = DOIPattern.matcher(input);
        while (doiMatcher.find()) {
            if (doiMatcher.groupCount() == 1) {
                return doiMatcher.group(1);
            }
        }

        return null;
    }

    /**
     * Methods borrowed from GROBID Consolidation.java
     */

    /**
     * The new public CrossRef API is a search API, and thus returns
     * many false positives. It is necessary to validate return results
     * against the (incomplete) source bibliographic item to block
     * inconsistent results.
     */
    private boolean areMetadataMatching(String title, String firstAuthor, MatchingDocument result) {
        boolean valid = true;

        // check main metadata available in source with fuzzy matching
        if (!StringUtils.isBlank(title) && !StringUtils.isBlank(title)) {
            if (ratcliffObershelpDistance(title, result.getTitle(), false) < 0.8)
                return false;
        }

        if (StringUtils.isNotBlank(firstAuthor) &&
                StringUtils.isNotBlank(result.getFirstAuthor())) {
            if (ratcliffObershelpDistance(firstAuthor, result.getFirstAuthor(), false) < 0.8)
                return false;
        }

        return valid;
    }

    private double ratcliffObershelpDistance(String string1, String string2, boolean caseDependent) {
        if (StringUtils.isBlank(string1) || StringUtils.isBlank(string2))
            return 0.0;
        Double similarity = 0.0;

        if (!caseDependent) {
            string1 = string1.toLowerCase();
            string2 = string2.toLowerCase();
        }

        if (string1.equals(string2))
            similarity = 1.0;
        if ((string1.length() > 0) && (string2.length() > 0)) {
            Option<Object> similarityObject =
                    RatcliffObershelpMetric.compare(string1, string2);
            if ((similarityObject != null) && (similarityObject.get() != null))
                similarity = (Double) similarityObject.get();
        }

        return similarity;
    }


    protected String injectIdsByDoi(String jsonobj, String doi) {
        final IstexData istexData = istexLookup.retrieveByDoi(doi);

        return injectIdsByIstexData(jsonobj, doi, istexData);
    }


    protected String injectIdsByIstexData(String jsonobj, String doi, IstexData istexData) {
        boolean pmid = false;
        boolean pmc = false;
        boolean foundIstexData = false;
        boolean foundPmidData = false;

        StringBuilder sb = new StringBuilder();
        sb.append(jsonobj, 0, length(jsonobj) - 1);

        if (istexData != null) {
            if (isNotBlank(istexData.getIstexId())) {
                sb.append(", \"istexId\":\"" + istexData.getIstexId() + "\"");
                foundIstexData = true;
            }
            if (CollectionUtils.isNotEmpty(istexData.getArk())) {
                sb.append(", \"ark\":\"" + istexData.getArk().get(0) + "\"");
                foundIstexData = true;
            }
            if (CollectionUtils.isNotEmpty(istexData.getPmid())) {
                sb.append(", \"pmid\":\"" + istexData.getPmid().get(0) + "\"");
                pmid = true;
                foundIstexData = true;
            }
            if (CollectionUtils.isNotEmpty(istexData.getPmc())) {
                sb.append(", \"pmcid\":\"" + istexData.getPmc().get(0) + "\"");
                pmc = true;
                foundIstexData = true;
            }
            if (CollectionUtils.isNotEmpty(istexData.getMesh())) {
                sb.append(", \"mesh\":\"" + istexData.getMesh().get(0) + "\"");
                foundIstexData = true;
            }
        }

        if (!pmid || !pmc) {
            final PmidData pmidData = pmidLookup.retrieveIdsByDoi(doi);
            if (pmidData != null) {
                if (isNotBlank(pmidData.getPmid())) {
                    sb.append(", \"pmid\":\"" + pmidData.getPmid() + "\"");
                    foundPmidData = true;
                }

                if (isNotBlank(pmidData.getPmcid())) {
                    sb.append(", \"pmcid\":\"" + pmidData.getPmcid() + "\"");
                    foundPmidData = true;
                }
            }
        }

        if (foundIstexData || foundPmidData) {
            sb.append("}");
            return sb.toString();
        } else {
            return jsonobj;
        }
    }

}
