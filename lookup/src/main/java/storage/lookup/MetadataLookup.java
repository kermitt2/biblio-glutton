package storage.lookup;

import exception.ServiceException;
import org.apache.http.HttpHost;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import storage.StorageEnvFactory;
import web.configuration.LookupConfiguration;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Lookup metadata -> doi
 */
public class MetadataLookup {
    private static final Logger LOGGER = LoggerFactory.getLogger(MetadataLookup.class);

    private LookupConfiguration configuration;
    private RestHighLevelClient esClient;

    public static final String INDEX_FIELD_NAME_TITLE = "title";
    public static final String INDEX_FIELD_NAME_FIRST_PAGE = "first_page";
    public static final String INDEX_FIELD_NAME_FIRST_AUTHOR = "first_author";
    public static final String INDEX_FIELD_NAME_DOI = "DOI";
    public static final String INDEX_FIELD_NAME_VOLUME = "volume";
    public static final String INDEX_FIELD_NAME_ISSN = "issn";
    public static final String INDEX_FIELD_NAME_JOURNAL_TITLE = "journal";
    public static final String INDEX_FIELD_ABBREVIATED_JOURNAL_TITLE = "abbreviated_journal";

    public MetadataLookup(StorageEnvFactory storageEnvFactory) {
        configuration = storageEnvFactory.getConfiguration();
        esClient = new RestHighLevelClient(
                RestClient.builder(HttpHost.create(configuration.getElastic().getHost())));
    }

    public long getSize() {
        try {
            SearchRequest searchRequest = new SearchRequest(configuration.getElastic().getIndex());

            SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
            return response.getHits().getTotalHits();
        } catch (IOException e) {
            throw new ServiceException(502, "Error while fetching cardinality for "
                    + configuration.getElastic().getIndex() + " index.", e);
        }
    }

    /**
     * Lookup by DOI
     **/
    public String retrieveByMetadata(String doi) {
        if (isBlank(doi)) {
            throw new ServiceException(401, "Supplied doi is null.");
        }
        final BoolQueryBuilder query = QueryBuilders.boolQuery()
                .should(QueryBuilders.termQuery(INDEX_FIELD_NAME_DOI, doi));

        return executeQuery(query);
    }

    /**
     * Lookup by title, firstAuthor
     **/
    public String retrieveByMetadata(String title, String firstAuthor) {
        if (isBlank(title) || isBlank(firstAuthor)) {
            throw new ServiceException(401, "Supplied title or firstAuthor are null.");
        }

        final BoolQueryBuilder query = QueryBuilders.boolQuery()
                .should(QueryBuilders.termQuery(INDEX_FIELD_NAME_TITLE, title))
                .should(QueryBuilders.termQuery(INDEX_FIELD_NAME_FIRST_AUTHOR, firstAuthor));

        return executeQuery(query);
    }

    /**
     * Lookup by journal title, journal abbreviated title, volume, first page
     **/
    public String retrieveByMetadata(String journalTitle, String abbreviatedJournalTitle, String volume,
                                     String firstPage) {

        if (isBlank(journalTitle)
                || isBlank(abbreviatedJournalTitle)
                || isBlank(volume)
                || isBlank(firstPage)) {
            throw new ServiceException(401, "Supplied journalTitle or abbr journal title or volume, or first page are null.");
        }

        final BoolQueryBuilder query = QueryBuilders.boolQuery()
                .should(QueryBuilders.termQuery(INDEX_FIELD_NAME_JOURNAL_TITLE, journalTitle))
                .should(QueryBuilders.termQuery(INDEX_FIELD_ABBREVIATED_JOURNAL_TITLE, abbreviatedJournalTitle))
                .should(QueryBuilders.termQuery(INDEX_FIELD_NAME_VOLUME, volume))
                .should(QueryBuilders.termQuery(INDEX_FIELD_NAME_FIRST_PAGE, firstPage));

        return executeQuery(query);
    }

    private String executeQuery(BoolQueryBuilder query) {
        SearchSourceBuilder builder = new SearchSourceBuilder();
        builder.query(query);
        builder.from(0);
        builder.size(1);

        final SearchRequest searchRequest = new SearchRequest(configuration.getElastic().getIndex());
        searchRequest.searchType(SearchType.DFS_QUERY_THEN_FETCH);
        searchRequest.source(builder);

        try {
            SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
            SearchHits hits = response.getHits();
            Iterator<SearchHit> it = hits.iterator();

            while (it.hasNext()) {
                SearchHit hit = it.next();

                List<String> jsonObj = (List<String>) hit.getSourceAsMap().get("jsondoc");

                return jsonObj.stream().collect(Collectors.joining(""));
            }
        } catch (IOException e) {
            throw new ServiceException(502, "Cannot fetch data from Elasticsearch. ", e);
        }

        throw new ServiceException(404, "Cannot find records for the input query. ");
    }
}
