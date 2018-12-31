package com.scienceminer.lookup.storage.lookup;

import com.scienceminer.lookup.configuration.LookupConfiguration;
import com.scienceminer.lookup.data.MatchingDocument;
import com.scienceminer.lookup.exception.NotFoundException;
import com.scienceminer.lookup.exception.ServiceException;
import com.scienceminer.lookup.storage.lookup.async.ESClientWrapper;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.http.HttpHost;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
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
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

import static org.apache.commons.lang3.StringUtils.isBlank;

public class MetadataMatching {
    private static final Logger LOGGER = LoggerFactory.getLogger(MetadataMatching.class);

    private LookupConfiguration configuration;
    private ESClientWrapper esClient;
    private MetadataLookup metadataLookup;

    public static final String INDEX_FIELD_NAME_ID = "id";
    public static final String INDEX_FIELD_NAME_TITLE = "title";
    public static final String INDEX_FIELD_NAME_FIRST_PAGE = "first_page";
    public static final String INDEX_FIELD_NAME_FIRST_AUTHOR = "first_author";
    public static final String INDEX_FIELD_NAME_DOI = "DOI";
    public static final String INDEX_FIELD_NAME_VOLUME = "volume";
    public static final String INDEX_FIELD_NAME_ISSN = "issn";
    public static final String INDEX_FIELD_NAME_BIBLIOGRAPHIC = "bibliographic";
    public static final String INDEX_FIELD_NAME_JOURNAL_TITLE = "journal";
    public static final String INDEX_FIELD_ABBREVIATED_JOURNAL_TITLE = "abbreviated_journal";

    private final String INDEX_FIELD_NAME_JSONDOC = "jsondoc";


    public MetadataMatching(LookupConfiguration configuration, MetadataLookup metadataLookup) {
        this.configuration = configuration;

        RestHighLevelClient esClient = new RestHighLevelClient(
                RestClient.builder(
                        HttpHost.create(configuration.getElastic().getHost()))
                        .setRequestConfigCallback(
                                requestConfigBuilder -> requestConfigBuilder
                                        .setConnectTimeout(30000)
                                        .setSocketTimeout(60000))
                        .setMaxRetryTimeoutMillis(120000));

        final int poolSize = configuration.getMaxAcceptedRequests() < 1 ? Runtime.getRuntime().availableProcessors() : configuration.getMaxAcceptedRequests();
        this.esClient = new ESClientWrapper(esClient, poolSize);

        this.metadataLookup = metadataLookup;

    }

    public long getSize() {
        try {
            SearchRequest searchRequest = new SearchRequest(configuration.getElastic().getIndex());

            SearchResponse response = esClient.searchSync(searchRequest, RequestOptions.DEFAULT);
            return response.getHits().getTotalHits();
        } catch (IOException e) {
            LOGGER.error("Error while contacting Elasticsearch to fetch the size of "
                    + configuration.getElastic().getIndex() + " index.", e);
        }

        return 0L;
    }

    /**
     * Lookup by title, firstAuthor
     **/
    public MatchingDocument retrieveByMetadata(String title, String firstAuthor) {
        validateInput(title, firstAuthor);

        final BoolQueryBuilder query = QueryBuilders.boolQuery()
                .should(QueryBuilders.matchQuery(INDEX_FIELD_NAME_TITLE, title))
                .should(QueryBuilders.matchQuery(INDEX_FIELD_NAME_FIRST_AUTHOR, firstAuthor));

        return executeQuery(query);
    }

    public void retrieveByMetadataAsync(String title, String firstAuthor,
                                        Consumer<MatchingDocument> callback) {
        validateInput(title, firstAuthor);

        final BoolQueryBuilder query = QueryBuilders.boolQuery()
                .should(QueryBuilders.matchQuery(INDEX_FIELD_NAME_TITLE, title))
                .should(QueryBuilders.matchQuery(INDEX_FIELD_NAME_FIRST_AUTHOR, firstAuthor));

        executeQueryAsync(query, callback);
    }

    private void validateInput(String title, String firstAuthor) {
        if (isBlank(title) || isBlank(firstAuthor)) {
            throw new ServiceException(400, "Supplied title or firstAuthor are null.");
        }
    }

    /**
     * Lookup by journal title, journal abbreviated title, volume, first page
     **/
    public MatchingDocument retrieveByMetadata(String title, String volume,
                                               String firstPage) {

        validateInput(title, volume, firstPage);

        BoolQueryBuilder query = getQueryBuilderJournal(title, volume, firstPage);

        return executeQuery(query);
    }

    public void retrieveByMetadataAsync(String title, String volume,
                                        String firstPage,
                                        Consumer<MatchingDocument> callback) {

        validateInput(title, volume, firstPage);

        BoolQueryBuilder query = getQueryBuilderJournal(title, volume, firstPage);

        executeQueryAsync(query, callback);
    }

    private BoolQueryBuilder getQueryBuilderJournal(String title, String volume, String firstPage) {

        return QueryBuilders.boolQuery()
                .should(QueryBuilders.matchQuery(INDEX_FIELD_NAME_JOURNAL_TITLE, title))
                .should(QueryBuilders.matchQuery(INDEX_FIELD_ABBREVIATED_JOURNAL_TITLE, title))
                .should(QueryBuilders.termQuery(INDEX_FIELD_NAME_VOLUME, volume))
                .should(QueryBuilders.termQuery(INDEX_FIELD_NAME_FIRST_PAGE, firstPage));
    }

    private void validateInput(String title, String volume, String firstPage) {
        if (isBlank(title)
                || isBlank(volume)
                || isBlank(firstPage)) {
            throw new ServiceException(400, "Supplied journalTitle or abbr journal title or volume, or first page are null.");
        }
    }


    /**
     * Lookup by journal title, journal abbreviated title, volume, first page
     **/
    public MatchingDocument retrieveByMetadata(String title, String volume,
                                               String firstPage, String firstAuthor) {

        validateInput(title, volume, firstPage, firstAuthor);

        final BoolQueryBuilder query = getQueryBuilderJournal(title, volume, firstPage, firstAuthor);

        return executeQuery(query);
    }

    private void validateInput(String title, String volume, String firstPage, String firstAuthor) {
        if (isBlank(title)
                || isBlank(volume)
                || isBlank(firstPage)
                || isBlank(firstAuthor)) {
            throw new ServiceException(400, "Supplied journalTitle or abbr journal title or volume, or first page are null.");
        }
    }

    public void retrieveByMetadataAsync(String title, String volume,
                                        String firstPage, String firstAuthor,
                                        Consumer<MatchingDocument> callback) {

        validateInput(title, volume, firstPage, firstAuthor);

        final BoolQueryBuilder query = getQueryBuilderJournal(title, volume, firstPage, firstAuthor);

        executeQueryAsync(query, callback);
    }

    private BoolQueryBuilder getQueryBuilderJournal(String title, String volume, String firstPage, String firstAuthor) {
        return QueryBuilders.boolQuery()
                .should(QueryBuilders.matchQuery(INDEX_FIELD_NAME_JOURNAL_TITLE, title))
                .should(QueryBuilders.matchQuery(INDEX_FIELD_ABBREVIATED_JOURNAL_TITLE, title))
                .should(QueryBuilders.termQuery(INDEX_FIELD_NAME_VOLUME, volume))
                .should(QueryBuilders.termQuery(INDEX_FIELD_NAME_FIRST_PAGE, firstPage))
                .should(QueryBuilders.termQuery(INDEX_FIELD_NAME_FIRST_AUTHOR, firstAuthor));
    }

    public MatchingDocument retrieveByBiblio(String biblio) {
        if (isBlank(biblio)) {
            throw new ServiceException(400, "Supplied bibliographical string is empty.");
        }

        final MatchQueryBuilder query = QueryBuilders.matchQuery(INDEX_FIELD_NAME_BIBLIOGRAPHIC, biblio);

        return executeQuery(query);
    }

    public void retrieveByBiblioAsync(String biblio, Consumer<MatchingDocument> callback) {
        if (isBlank(biblio)) {
            throw new ServiceException(400, "Supplied bibliographical string is empty.");
        }

        final MatchQueryBuilder query = QueryBuilders.matchQuery(INDEX_FIELD_NAME_BIBLIOGRAPHIC, biblio);

        executeQueryAsync(query, callback);
    }

    private MatchingDocument executeQuery(QueryBuilder query) {
        SearchRequest request = prepareQueryExecution(query);
        final MatchingDocument matchingDocument;
        try {
            final SearchResponse searchResponse = esClient.searchSync(request, RequestOptions.DEFAULT);

            matchingDocument = processResponse(searchResponse);

            if (matchingDocument.isException()) {
                if(matchingDocument.getException() instanceof NotFoundException) {
                    throw (NotFoundException) matchingDocument.getException();
                }

                throw (Exception) matchingDocument.getException();
            }
        } catch (IOException e) {
            throw new ServiceException(500, "No response from Elasticsearch. ", e);
        } catch (Exception e) {
            throw new ServiceException(500, "Elasticsearch server error. ", e);
        }

        return matchingDocument;
    }


    private void executeQueryAsync(QueryBuilder query, Consumer<MatchingDocument> callback) {
        SearchRequest searchRequest = prepareQueryExecution(query);
        try {
            esClient.searchAsync(searchRequest, RequestOptions.DEFAULT, (response, exception) -> {
                if (exception == null) {
                    callback.accept(processResponse(response));
                } else {
                    callback.accept(new MatchingDocument(exception));
                }
            });
        } catch (Exception e) {
            callback.accept(new MatchingDocument(e));
        }
    }

    private SearchRequest prepareQueryExecution(QueryBuilder query) {
        SearchSourceBuilder builder = new SearchSourceBuilder();
        builder.query(query);
        builder.from(0);
        builder.size(1);

        String[] includeFields = new String[]
                {
                        INDEX_FIELD_NAME_ID,
                        INDEX_FIELD_NAME_DOI,
                        INDEX_FIELD_NAME_FIRST_AUTHOR,
                        INDEX_FIELD_NAME_TITLE
                };
        String[] excludeFields = new String[]{"*"};
        builder.fetchSource(includeFields, null);

        final SearchRequest searchRequest = new SearchRequest(configuration.getElastic().getIndex());
        searchRequest.searchType(SearchType.DFS_QUERY_THEN_FETCH);
        searchRequest.source(builder);

        return searchRequest;
    }

    private MatchingDocument processResponse(SearchResponse response) {
        SearchHits hits = response.getHits();
        Iterator<SearchHit> it = hits.iterator();
        final MatchingDocument matchingDocument = new MatchingDocument();

        while (it.hasNext()) {
            SearchHit hit = it.next();

            String DOI = (String) hit.getSourceAsMap().get(INDEX_FIELD_NAME_DOI);
            String firstAuthor = (String) hit.getSourceAsMap().get(INDEX_FIELD_NAME_FIRST_AUTHOR);
            final List<String> titles = (List<String>) hit.getSourceAsMap().get(INDEX_FIELD_NAME_TITLE);
            String title = "";
            if (CollectionUtils.isNotEmpty(titles)) {
                title = titles.get(0);
            }

            matchingDocument.setDOI(DOI);
            matchingDocument.setFirstAuthor(firstAuthor);
            matchingDocument.setTitle(title);
            final String jsonObject = metadataLookup.retrieveJsonDocument(DOI);
            if (jsonObject == null) {
                matchingDocument.setException(new NotFoundException("The index returned a result but the body cannot be fetched. Doi: " + DOI));
                return matchingDocument;
            }
            matchingDocument.setJsonObject(jsonObject);

            return matchingDocument;
        }

        matchingDocument.setIsException(true);
        matchingDocument.setException(new NotFoundException("Cannot find records for the input query."));
        return matchingDocument;
    }
}
