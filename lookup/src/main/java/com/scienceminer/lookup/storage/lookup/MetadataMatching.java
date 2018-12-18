package com.scienceminer.lookup.storage.lookup;

import com.scienceminer.lookup.configuration.LookupConfiguration;
import com.scienceminer.lookup.data.MatchingDocument;
import com.scienceminer.lookup.exception.NotFoundException;
import com.scienceminer.lookup.exception.ServiceException;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.http.HttpHost;
import org.elasticsearch.action.ActionListener;
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import static org.apache.commons.lang3.StringUtils.isBlank;

public class MetadataMatching {
    private static final Logger LOGGER = LoggerFactory.getLogger(MetadataMatching.class);

    private LookupConfiguration configuration;
    private RestHighLevelClient esClient;
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
        
        esClient = new RestHighLevelClient(
                RestClient.builder(
                        HttpHost.create(configuration.getElastic().getHost()))
                        .setRequestConfigCallback(
                                requestConfigBuilder -> requestConfigBuilder
                                        .setConnectTimeout(30000)
                                        .setSocketTimeout(60000))
                        .setMaxRetryTimeoutMillis(120000));

        this.metadataLookup = metadataLookup;

    }

    public void getSize() {
        Map<String, Long> sizes = new HashMap<>();
        sizes.put("elasticsearch", 0L);

        try {
            SearchRequest searchRequest = new SearchRequest(configuration.getElastic().getIndex());

            SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
            sizes.put("elasticsearch", response.getHits().getTotalHits());
        } catch (IOException e) {
            LOGGER.error("Error while contacting Elasticsearch to fetch the size of "
                    + configuration.getElastic().getIndex() + " index.", e);
        }
    }

    /**
     * Lookup by title, firstAuthor
     **/
    public MatchingDocument retrieveByMetadata(String title, String firstAuthor) {
        if (isBlank(title) || isBlank(firstAuthor)) {
            throw new ServiceException(400, "Supplied title or firstAuthor are null.");
        }

        final BoolQueryBuilder query = QueryBuilders.boolQuery()
                .should(QueryBuilders.matchQuery(INDEX_FIELD_NAME_TITLE, title))
                .should(QueryBuilders.matchQuery(INDEX_FIELD_NAME_FIRST_AUTHOR, firstAuthor));

        return executeQuery(query);
    }

    /**
     * Lookup by journal title, journal abbreviated title, volume, first page
     **/
    public MatchingDocument retrieveByMetadata(String title, String volume,
                                               String firstPage) {

        if (isBlank(title)
                || isBlank(volume)
                || isBlank(firstPage)) {
            throw new ServiceException(400, "Supplied journalTitle or abbr journal title or volume, or first page are null.");
        }

        final BoolQueryBuilder query = QueryBuilders.boolQuery()
                .should(QueryBuilders.matchQuery(INDEX_FIELD_NAME_JOURNAL_TITLE, title))
                .should(QueryBuilders.matchQuery(INDEX_FIELD_ABBREVIATED_JOURNAL_TITLE, title))
                .should(QueryBuilders.termQuery(INDEX_FIELD_NAME_VOLUME, volume))
                .should(QueryBuilders.termQuery(INDEX_FIELD_NAME_FIRST_PAGE, firstPage));

        return executeQuery(query);
    }

    /**
     * Lookup by journal title, journal abbreviated title, volume, first page
     **/
    public MatchingDocument retrieveByMetadata(String title, String volume,
                                               String firstPage, String firstAuthor) {

        if (isBlank(title)
                || isBlank(volume)
                || isBlank(firstPage)) {
            throw new ServiceException(400, "Supplied journalTitle or abbr journal title or volume, or first page are null.");
        }

        final BoolQueryBuilder query = QueryBuilders.boolQuery()
                .should(QueryBuilders.matchQuery(INDEX_FIELD_NAME_JOURNAL_TITLE, title))
                .should(QueryBuilders.matchQuery(INDEX_FIELD_ABBREVIATED_JOURNAL_TITLE, title))
                .should(QueryBuilders.termQuery(INDEX_FIELD_NAME_VOLUME, volume))
                .should(QueryBuilders.termQuery(INDEX_FIELD_NAME_FIRST_PAGE, firstPage))
                .should(QueryBuilders.termQuery(INDEX_FIELD_NAME_FIRST_AUTHOR, firstAuthor));

        return executeQuery(query);
    }

    private MatchingDocument executeQuery(QueryBuilder query) {
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

//        try {
//            return processResponse(esClient.search(searchRequest, RequestOptions.DEFAULT));
//
//        } catch (IOException e) {
//            throw new ServiceException(503, "No response from Elasticsearch. ", e);
//        } catch (Exception e) {
//            throw new ServiceException(503, "Elasticsearch server error. ", e);
//        }


        final MatchingDocument matchingDocument = new MatchingDocument();

        final ActionListener<SearchResponse> listener = new ActionListener<SearchResponse>() {
            @Override
            public void onResponse(SearchResponse searchResponse) {
                final MatchingDocument matchingDocument1 = processResponse(searchResponse);
                matchingDocument.fillFromMatchindDocument(matchingDocument1);
            }

            @Override
            public void onFailure(Exception e) {
                throw new ServiceException(503, "Elasticsearch server error. ", e);
            }
        };

        new Thread(() -> esClient.searchAsync(searchRequest, RequestOptions.DEFAULT, listener));

        

        return matchingDocument;

    }

    private MatchingDocument processResponse(SearchResponse response) {
        SearchHits hits = response.getHits();
        Iterator<SearchHit> it = hits.iterator();

        while (it.hasNext()) {
            SearchHit hit = it.next();

            String DOI = (String) hit.getSourceAsMap().get(INDEX_FIELD_NAME_DOI);
            String firstAuthor = (String) hit.getSourceAsMap().get(INDEX_FIELD_NAME_FIRST_AUTHOR);
            final List<String> titles = (List<String>) hit.getSourceAsMap().get(INDEX_FIELD_NAME_TITLE);
            String title = "";
            if (CollectionUtils.isNotEmpty(titles)) {
                title = titles.get(0);
            }

            final MatchingDocument matchingDocument = new MatchingDocument(DOI);
            matchingDocument.setFirstAuthor(firstAuthor);
            matchingDocument.setTitle(title);
            final String jsonObject = metadataLookup.retrieveJsonDocument(DOI);
            if (jsonObject == null) {
                throw new NotFoundException("The index returned a result but the body cannot be fetched. Doi: " + DOI);
            }
            matchingDocument.setJsonObject(jsonObject);

            return matchingDocument;
        }
        throw new NotFoundException("Cannot find records for the input query. ");
    }

    public MatchingDocument retrieveByBiblio(String biblio) {
        if (isBlank(biblio)) {
            throw new ServiceException(400, "Supplied bibliographical string is empty.");
        }

        final MatchQueryBuilder query = QueryBuilders.matchQuery(INDEX_FIELD_NAME_BIBLIOGRAPHIC, biblio);

        return executeQuery(query);
    }
}
