package com.scienceminer.glutton.storage.lookup;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.scienceminer.glutton.configuration.LookupConfiguration;
import com.scienceminer.glutton.data.MatchingDocument;
import com.scienceminer.glutton.exception.NotFoundException;
import com.scienceminer.glutton.exception.ServiceException;
import com.scienceminer.glutton.storage.lookup.async.ESClientWrapper;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.http.HttpHost;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.WarningsHandler;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.client.core.CountResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class MetadataMatching {
    private static final Logger LOGGER = LoggerFactory.getLogger(MetadataMatching.class);

    public final static List<String> preprintPrefixes = List.of("10.1101", "10.36227", "10.48550");
    public final static Double preprintThreshold = 0.99;

    private static volatile MetadataMatching instance;

    private LookupConfiguration configuration;
    private ESClientWrapper esClient;
    private CrossrefMetadataLookup crossrefMetadataLookup;
    private HALLookup halLookup;

    public static final String INDEX_FIELD_NAME_ID = "id";
    public static final String INDEX_FIELD_NAME_ATITLE = "title";
    public static final String INDEX_FIELD_NAME_FIRST_PAGE = "first_page";
    public static final String INDEX_FIELD_NAME_FIRST_AUTHOR = "first_author";
    public static final String INDEX_FIELD_NAME_DOI = "DOI";
    public static final String INDEX_FIELD_NAME_HALID = "halId";
    public static final String INDEX_FIELD_NAME_PMID = "pmid";
    public static final String INDEX_FIELD_NAME_VOLUME = "volume";
    public static final String INDEX_FIELD_NAME_ISSN = "issn";
    public static final String INDEX_FIELD_NAME_BIBLIOGRAPHIC = "bibliographic";
    public static final String INDEX_FIELD_NAME_JTITLE = "journal";
    public static final String INDEX_FIELD_ABBREVIATED_JOURNAL_TITLE = "abbreviated_journal";
    public static final String INDEX_FIELD_NAME_YEAR = "year";
    public static final String INDEX_FIELD_NAME_ABBREV_TITLE = "abbreviated_journal";
    public static final String INDEX_FIELD_NAME_SOURCE = "source";

    private final String INDEX_FIELD_NAME_JSONDOC = "jsondoc";

    public static MetadataMatching getInstance(LookupConfiguration configuration, 
                                               CrossrefMetadataLookup crossrefMetadataLookup,
                                               HALLookup halLookup) {
        if (instance == null) {
            synchronized (MetadataMatching.class) {
                if (instance == null) {
                    getNewInstance(configuration, crossrefMetadataLookup, halLookup);
                }
            }
        }
        return instance;
    }

    /**
     * Creates a new instance.
     */
    private static synchronized void getNewInstance(LookupConfiguration configuration,
                                                    CrossrefMetadataLookup crossrefMetadataLookup,
                                                    HALLookup halLookup) {
        instance = new MetadataMatching(configuration, crossrefMetadataLookup, halLookup);
    }

    private MetadataMatching(LookupConfiguration configuration, 
                            CrossrefMetadataLookup crossrefMetadataLookup, 
                            HALLookup halLookup) {
        this.configuration = configuration;
        RestHighLevelClient esClient = new RestHighLevelClient(
                RestClient.builder(
                        HttpHost.create(configuration.getElastic().getHost()))
                        .setRequestConfigCallback(
                                requestConfigBuilder -> requestConfigBuilder
                                        .setConnectTimeout(30000)
                                        .setSocketTimeout(60000))
                        .setHttpClientConfigCallback(
                                httpAsyncClientBuilder -> httpAsyncClientBuilder
                                        .setMaxConnPerRoute(configuration.getElastic().getMaxConnections())
                                        .setMaxConnTotal(configuration.getElastic().getMaxConnections())));

        // note: maxRetryTimeoutMillis is deprecated in ES 7 due to implementation issue 
        // https://github.com/elastic/elasticsearch/pull/38085
        //                .setMaxRetryTimeoutMillis(120000));

        this.esClient = new ESClientWrapper(esClient, configuration.getMaxAcceptedRequests());

        this.crossrefMetadataLookup = crossrefMetadataLookup;
        this.halLookup = halLookup;
    }

    public long getSize() {
        try {
            CountRequest countRequest = new CountRequest(); 
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder(); 
            searchSourceBuilder.query(QueryBuilders.matchAllQuery()); 
            countRequest.source(searchSourceBuilder);

            CountResponse countResponse = esClient.count(countRequest, RequestOptions.DEFAULT);
            return countResponse.getCount();
        } catch (IOException e) {
            LOGGER.error("Error while contacting Elasticsearch to fetch the size of "
                    + configuration.getElastic().getIndex() + " index.", e);
        }

        return 0L;
    }

    /**
     * Boolean search by article title, firstAuthor
     **/
    public List<MatchingDocument> retrieveByMetadata(String atitle, 
                                                     String firstAuthor) {
        validateInput(atitle, firstAuthor);

        final BoolQueryBuilder query = QueryBuilders.boolQuery()
                .should(QueryBuilders.matchQuery(INDEX_FIELD_NAME_ATITLE, atitle))
                .should(QueryBuilders.matchQuery(INDEX_FIELD_NAME_FIRST_AUTHOR, firstAuthor));

        return executeQuery(query);
    }

    /**
     * Async boolean search by article title, firstAuthor
     **/
    public void retrieveByMetadataAsync(String atitle, 
                                        String firstAuthor,
                                        Consumer<List<MatchingDocument>> callback) {
        validateInput(atitle, firstAuthor);

        final BoolQueryBuilder query = QueryBuilders.boolQuery()
                .should(QueryBuilders.matchQuery(INDEX_FIELD_NAME_ATITLE, atitle))
                .should(QueryBuilders.matchQuery(INDEX_FIELD_NAME_FIRST_AUTHOR, firstAuthor));

        executeQueryAsync(query, callback);
    }

    private void validateInput(String title, 
                               String firstAuthor) {
        if (isBlank(title) || isBlank(firstAuthor)) {
            throw new ServiceException(400, "Supplied title and/or first author is null.");
        }
    }

    private void validateInput(String title, String volume, String firstPage) {
        if (isBlank(title)
                || isBlank(volume)
                || isBlank(firstPage)) {
            throw new ServiceException(400, 
                "At least one of supplied journal title, abbreviated journal title, volume, or first page is null.");
        }
    }

    /**
     * Boolean search by journal title or abbreviated journal title, volume, first page and first author
     **/
    public List<MatchingDocument> retrieveByMetadata(String jtitle, 
                                                     String volume,
                                                     String firstPage, 
                                                     String firstAuthor) {

        validateInput(jtitle, volume, firstPage);

        final BoolQueryBuilder query = getQueryBuilderJournal(jtitle, volume, firstPage, firstAuthor);

        return executeQuery(query);
    }

    /**
     * Async boolean search by journal title or abbreviated journal title, volume, first page and first author
     **/
    public void retrieveByMetadataAsync(String jtitle, 
                                        String volume,
                                        String firstPage, 
                                        String firstAuthor,
                                        Consumer<List<MatchingDocument>> callback) {

        validateInput(jtitle, volume, firstPage);

        final BoolQueryBuilder query = getQueryBuilderJournal(jtitle, volume, firstPage, firstAuthor);

        executeQueryAsync(query, callback);
    }

    private BoolQueryBuilder getQueryBuilderJournal(String jtitle, 
                                                    String volume, 
                                                    String firstPage, 
                                                    String firstAuthor) {
        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery()
                .should(QueryBuilders.matchQuery(INDEX_FIELD_NAME_JTITLE, jtitle))
                .should(QueryBuilders.matchQuery(INDEX_FIELD_ABBREVIATED_JOURNAL_TITLE, jtitle))
                .must(QueryBuilders.termQuery(INDEX_FIELD_NAME_VOLUME, volume))
                .must(QueryBuilders.termQuery(INDEX_FIELD_NAME_FIRST_PAGE, firstPage));

        if (!isBlank(firstAuthor)) {
            queryBuilder = queryBuilder.should(QueryBuilders.termQuery(INDEX_FIELD_NAME_FIRST_AUTHOR, firstAuthor));
        } 

        return queryBuilder;
    }

    /**
     * Search by raw bibliographical reference string
     */
    public List<MatchingDocument> retrieveByBiblio(String biblio) {
        if (isBlank(biblio)) {
            throw new ServiceException(400, "Supplied bibliographical string is empty.");
        }

        final MatchQueryBuilder query = QueryBuilders.matchQuery(INDEX_FIELD_NAME_BIBLIOGRAPHIC, biblio);

        return executeQuery(query);
    }

    /**
     * Async search by raw bibliographical reference string
     **/
    public void retrieveByBiblioAsync(String biblio, 
                                    Consumer<List<MatchingDocument>> callback) {
        if (isBlank(biblio)) {
            throw new ServiceException(400, "Supplied bibliographical string is empty.");
        }
        final MatchQueryBuilder query = QueryBuilders.matchQuery(INDEX_FIELD_NAME_BIBLIOGRAPHIC, biblio);

        executeQueryAsync(query, callback);
    }

    /**
     * Async search by raw bibliographical reference string
     **/
    public void retrieveByBiblioAsyncConditional(String biblio, 
                                    List<String> sources, 
                                    String toIgnore,
                                    Consumer<List<MatchingDocument>> callback) {
        if (isBlank(biblio)) {
            throw new ServiceException(400, "Supplied bibliographical string is empty.");
        }
        //MatchQueryBuilder query = null;
        BoolQueryBuilder query = null;
        if (toIgnore == null) {
            // usual case
            //query = QueryBuilders.matchQuery(INDEX_FIELD_NAME_BIBLIOGRAPHIC, biblio);
            query = QueryBuilders.boolQuery()
                .must(QueryBuilders.existsQuery("DOI"))
                .should(QueryBuilders.matchQuery(INDEX_FIELD_NAME_BIBLIOGRAPHIC, biblio));
        } else {
            //query = QueryBuilders.matchQuery(INDEX_FIELD_NAME_BIBLIOGRAPHIC, biblio);
            query = QueryBuilders.boolQuery()
                .should(QueryBuilders.matchQuery(INDEX_FIELD_NAME_BIBLIOGRAPHIC, biblio))
                .mustNot(QueryBuilders.termQuery("_id", toIgnore));
        }

        executeQueryAsync(query, callback);
    }

    private List<MatchingDocument> executeQuery(QueryBuilder query) {
        SearchRequest request = prepareQueryExecution(query);
        final List<MatchingDocument> matchingDocuments;
        try {
            final SearchResponse searchResponse = esClient.searchSync(request, RequestOptions.DEFAULT);

            matchingDocuments = processResponse(searchResponse);

            if (matchingDocuments.size() == 0) {
                // It should not be the case, in case of search failure we have one MatchingDocument
                // with an exception. We can consider this case as not found case
                throw new NotFoundException("Cannot find records for the input query.");
            }
            else if (matchingDocuments.get(0).isException()) {
                if(matchingDocuments.get(0).getException() instanceof NotFoundException) {
                    throw (NotFoundException) matchingDocuments.get(0).getException();
                }

                throw (Exception) matchingDocuments.get(0).getException();
            }
        } catch (IOException e) {
            throw new ServiceException(500, "No response from Elasticsearch. ", e);
        } catch (Exception e) {
            throw new ServiceException(500, "Elasticsearch server error. ", e);
        }

        return matchingDocuments;
    }

    private void executeQueryAsync(QueryBuilder query, Consumer<List<MatchingDocument>> callback) {
        SearchRequest searchRequest = prepareQueryExecution(query);
        try {
            RequestOptions options = RequestOptions.DEFAULT;
            RequestOptions.Builder builder = options.toBuilder();
            builder.setWarningsHandler(WarningsHandler.PERMISSIVE);
            esClient.searchAsync(searchRequest, builder.build(), (response, exception) -> {
                if (exception == null) {
                    List<MatchingDocument> matchingDocuments = processResponse(response);
                    if (matchingDocuments.size() > 1 && matchingDocuments.get(1).getBlockingScore() > matchingDocuments.get(0).getBlockingScore() * preprintThreshold) {
                        for (String preprintPrefix : preprintPrefixes) {
                            if (matchingDocuments.get(0).getDOI().startsWith(preprintPrefix) ) {
                                Collections.swap(matchingDocuments, 0, 1);
                                break;
                            }
                        }
                    }
                    LOGGER.info("Found {} matching documents: {}",
                            matchingDocuments.size(),
                            Arrays.toString(
                                    matchingDocuments.stream()
                                            .map(d -> d.getDOI() + "###" + d.getBlockingScore())
                                            .toArray()));
                    callback.accept(matchingDocuments);
                } else {
                    List<MatchingDocument> matchingDocuments = new ArrayList<>();
                    matchingDocuments.add(new MatchingDocument(exception));
                    callback.accept(matchingDocuments);
                }
            });
        } catch (Exception e) {
            List<MatchingDocument> matchingDocuments = new ArrayList<>();
            matchingDocuments.add(new MatchingDocument(e));
            callback.accept(matchingDocuments);
        }
    }

    private SearchRequest prepareQueryExecution(QueryBuilder query) {
        SearchSourceBuilder builder = new SearchSourceBuilder();
        builder.query(query);
        builder.from(0);
        builder.size(configuration.getBlockSize());

        String[] includeFields = new String[]
                {
                        INDEX_FIELD_NAME_ID,
                        INDEX_FIELD_NAME_DOI,
                        INDEX_FIELD_NAME_HALID,
                        INDEX_FIELD_NAME_FIRST_AUTHOR,
                        INDEX_FIELD_NAME_ATITLE,
                        INDEX_FIELD_NAME_JTITLE,
                        INDEX_FIELD_NAME_YEAR
                };
        //String[] excludeFields = new String[]{"*"};
        builder.fetchSource(includeFields, null);

        final SearchRequest searchRequest = new SearchRequest(configuration.getElastic().getIndex());
        searchRequest.searchType(SearchType.DFS_QUERY_THEN_FETCH);
        searchRequest.source(builder);
        return searchRequest;
    }

    private List<MatchingDocument> processResponse(SearchResponse response) {
        SearchHits hits = response.getHits();
        Iterator<SearchHit> it = hits.iterator();
        final List<MatchingDocument> matchingDocuments = new ArrayList<>();

        double scoreMin = 1.0;
        double scoreMax = 0.0;

        while (it.hasNext() && matchingDocuments.size() < configuration.getBlockSize()) {
            MatchingDocument matchingDocument = new MatchingDocument();

            SearchHit hit = it.next();

            String id = (String) hit.getSourceAsMap().get(INDEX_FIELD_NAME_ID);
            String DOI = (String) hit.getSourceAsMap().get(INDEX_FIELD_NAME_DOI);
            String halId = (String) hit.getSourceAsMap().get(INDEX_FIELD_NAME_HALID);
            //String pmid = (String) hit.getSourceAsMap().get(INDEX_FIELD_NAME_PMID);
            String firstAuthor = (String) hit.getSourceAsMap().get(INDEX_FIELD_NAME_FIRST_AUTHOR);

            final List<String> atitles = (List<String>) hit.getSourceAsMap().get(INDEX_FIELD_NAME_ATITLE);
            String atitle = "";
            if (CollectionUtils.isNotEmpty(atitles)) {
                atitle = atitles.get(0);
            }

            final List<String> jtitles = (List<String>) hit.getSourceAsMap().get(INDEX_FIELD_NAME_JTITLE);
            String jtitle = "";
            if (CollectionUtils.isNotEmpty(jtitles)) {
                jtitle = jtitles.get(0);
            }

            final List<String> abbrevTtitles = (List<String>) hit.getSourceAsMap().get(INDEX_FIELD_NAME_ABBREV_TITLE);
            String abbreviatedTitle = "";
            if (CollectionUtils.isNotEmpty(abbrevTtitles)) {
                abbreviatedTitle = abbrevTtitles.get(0);
            }

            final String year = (String) hit.getSourceAsMap().get(INDEX_FIELD_NAME_YEAR);
            String yearStr = null;
            if (year != null) {
                yearStr = ""+year; 
            }

            matchingDocument.setId(id);
            matchingDocument.setDOI(DOI);
            matchingDocument.setHalId(halId);
            //matchingDocument.setPmid(pmid);
            matchingDocument.setFirstAuthor(firstAuthor);
            matchingDocument.setATitle(atitle);
            matchingDocument.setJTitle(jtitle);
            matchingDocument.setAbbreviatedTitle(abbreviatedTitle);
            matchingDocument.setYear(yearStr);

            String jsonObject = null;
            if (isNotBlank(DOI)) {
                jsonObject = crossrefMetadataLookup.retrieveJsonDocument(DOI);    
            }

            if (jsonObject == null && isNotBlank(halId)) {
                jsonObject = halLookup.retrieveJsonDocument(halId);
            }

            if (jsonObject == null) {
                LOGGER.warn("The search index returned a result but the corresponding entry cannot be fetched in the metadata db, DOI: " + 
                    DOI + ", Hal ID: " + halId);// + ", PMID: " + pmid);
                continue;
            }

            matchingDocument.setJsonObject(jsonObject);
            double hitScore = hit.getScore();
            matchingDocument.setBlockingScore(hitScore);
            if (hitScore > scoreMax)
                scoreMax = hitScore;
            if (hitScore < scoreMin)
                scoreMin = hitScore;

            try {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode item = mapper.readTree(jsonObject);
                if (item.isObject()) {
                    JsonNode authorsNode = item.get("author");
                    if (authorsNode != null && (!authorsNode.isMissingNode()) && 
                        authorsNode.isArray() && (((ArrayNode)authorsNode).size() > 0)) {
                        Iterator<JsonNode> authorIt = ((ArrayNode)authorsNode).elements();
                        while (authorIt.hasNext()) {
                            JsonNode authorNode = authorIt.next();

                            if (authorNode.get("family") != null && !authorNode.get("family").isMissingNode()) {
                                matchingDocument.setFirstAuthor(authorNode.get("family").asText());
                                break;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("Invalid JSON object", e);
            }

            matchingDocuments.add(matchingDocument);
        }

        if (matchingDocuments.size() == 0) {
            MatchingDocument matchingDocument = new MatchingDocument();
            matchingDocument.setIsException(true);
            matchingDocument.setException(new NotFoundException("Cannot find records for the input query."));
            matchingDocuments.add(matchingDocument);
        } else {
            // normalize search scores in [0.5:1]
            for(MatchingDocument matchingDocument : matchingDocuments) {
                double normalizedHitScore = (matchingDocument.getBlockingScore() - scoreMin) / (scoreMax - scoreMin);
                //normalizedHitScore = 0.5 + (normalizedHitScore/2);
                matchingDocument.setBlockingScore(normalizedHitScore);
            }
        }

        return matchingDocuments;
    }

    public void close() {
        this.crossrefMetadataLookup.close();
    }
}
