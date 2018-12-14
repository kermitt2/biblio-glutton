package com.scienceminer.lookup.storage.lookup;

import com.codahale.metrics.Meter;
import com.scienceminer.lookup.configuration.LookupConfiguration;
import com.scienceminer.lookup.data.MatchingDocument;
import com.scienceminer.lookup.exception.NotFoundException;
import com.scienceminer.lookup.exception.ServiceException;
import com.scienceminer.lookup.reader.CrossrefJsonReader;
import com.scienceminer.lookup.utils.BinarySerialiser;
import com.scienceminer.lookup.storage.StorageEnvFactory;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.action.ActionListener;
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
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.ElasticsearchException;
import org.lmdbjava.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.scienceminer.lookup.web.resource.DataController.DEFAULT_MAX_SIZE_LIST;
import static java.nio.ByteBuffer.allocateDirect;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.lowerCase;

/**
 * Lookup metadata -> doi
 */
public class MetadataLookup {
    private static final Logger LOGGER = LoggerFactory.getLogger(MetadataLookup.class);
    private final String INDEX_FIELD_NAME_JSONDOC = "jsondoc";

    private Env<ByteBuffer> environment;
    private Dbi<ByteBuffer> dbCrossrefJson;

    public static final String ENV_NAME = "crossref";

    public static final String NAME_CROSSREF_JSON = ENV_NAME + "_Jsondoc";
    private final int batchSize;

    private LookupConfiguration configuration;
    private RestHighLevelClient esClient;

    /*private LoadingCache<String, MatchingDocument> cacheByDoi = CacheBuilder.newBuilder()
            .maximumSize(1000000)
            .build(
                    new CacheLoader<String, MatchingDocument>() {
                        public MatchingDocument load(String doi) throws Exception {
                            return retrieveByMetadata(doi);
                        }
                    }
            );*/

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

    public MetadataLookup(StorageEnvFactory storageEnvFactory) {
        this.environment = storageEnvFactory.getEnv(ENV_NAME);

        configuration = storageEnvFactory.getConfiguration();
        batchSize = configuration.getBatchSize();
        esClient = new RestHighLevelClient(
        RestClient.builder(
            HttpHost.create(configuration.getElastic().getHost()))
            .setRequestConfigCallback(
                new RestClientBuilder.RequestConfigCallback() {
                    @Override
                    public RequestConfig.Builder customizeRequestConfig(
                            RequestConfig.Builder requestConfigBuilder) {
                        return requestConfigBuilder
                            .setConnectTimeout(5000)
                            .setSocketTimeout(60000);
                    }
                })
            .setMaxRetryTimeoutMillis(100000));

        dbCrossrefJson = this.environment.openDbi(NAME_CROSSREF_JSON, DbiFlags.MDB_CREATE);
    }

    public void loadFromFile(InputStream is, CrossrefJsonReader reader, Meter meter) {
        final TransactionWrapper transactionWrapper = new TransactionWrapper(environment.txnWrite());
        final AtomicInteger counter = new AtomicInteger(0);

        reader.load(is, crossrefData -> {
            if (counter.get() == batchSize) {
                transactionWrapper.tx.commit();
                transactionWrapper.tx.close();
                transactionWrapper.tx = environment.txnWrite();
                counter.set(0);
            }
            String key = lowerCase(crossrefData.get("DOI").asText());

            store(key, crossrefData.toString(), dbCrossrefJson, transactionWrapper.tx);
            meter.mark();
            counter.incrementAndGet();


        });
        transactionWrapper.tx.commit();
        transactionWrapper.tx.close();

        LOGGER.info("Cross checking number of records processed: " + meter.getCount());
    }

    private void store(String key, String value, Dbi<ByteBuffer> db, Txn<ByteBuffer> tx) {
        try {
            final ByteBuffer keyBuffer = allocateDirect(environment.getMaxKeySize());
            keyBuffer.put(BinarySerialiser.serialize(key)).flip();
            final byte[] serializedValue = BinarySerialiser.serializeAndCompress(value);
            final ByteBuffer valBuffer = allocateDirect(serializedValue.length);
            valBuffer.put(serializedValue).flip();
            db.put(tx, keyBuffer, valBuffer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Map<String, Long> getSize() {

        Map<String, Long> sizes = new HashMap<>();
        try (final Txn<ByteBuffer> txn = this.environment.txnRead()) {
            sizes.put(NAME_CROSSREF_JSON, dbCrossrefJson.stat(txn).entries);
        }

        sizes.put("elasticsearch", 0L);

        try {
            SearchRequest searchRequest = new SearchRequest(configuration.getElastic().getIndex());

            SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
            sizes.put("elasticsearch", response.getHits().getTotalHits());
        } catch (IOException e) {
            LOGGER.error("Error while contacting Elasticsearch to fetch the size of "
                    + configuration.getElastic().getIndex() + " index.", e);
        }

        return sizes;
    }

    public String retrieveJsonDocument(String doi) {
        final ByteBuffer keyBuffer = allocateDirect(environment.getMaxKeySize());
        ByteBuffer cachedData = null;
        String record = null;
        try (Txn<ByteBuffer> tx = environment.txnRead()) {
            keyBuffer.put(BinarySerialiser.serialize(doi)).flip();
            cachedData = dbCrossrefJson.get(tx, keyBuffer);
            if (cachedData != null) {
                record = (String) BinarySerialiser.deserializeAndDecompress(cachedData);
            }
        } catch (Exception e) {
            LOGGER.error("Cannot retrieve Crossref document by DOI:  " + doi, e);
        }

        return record;

    }

    /**
     * Lookup by DOI
     **/
    public MatchingDocument retrieveByMetadata(String doi) {
        if (isBlank(doi)) {
            throw new ServiceException(400, "The supplied DOI is null.");
        }
        final String jsonDocument = retrieveJsonDocument(lowerCase(doi));

        return new MatchingDocument(doi, jsonDocument);
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

        try {
            SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
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
                final String jsonObject = retrieveJsonDocument(DOI);
                if (jsonObject == null) {
                    throw new NotFoundException("The index returned a result but the body cannot be fetched. Doi: " + DOI);
                }
                matchingDocument.setJsonObject(jsonObject);

                return matchingDocument;
            }
        } catch (IOException e) {
            throw new ServiceException(503, "No response from Elasticsearch. ", e);
        } catch (Exception e) {
            throw new ServiceException(503, "Elasticsearch server error. ", e);
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

    public List<Pair<String, String>> retrieveList(Integer total) {
        return retrieveList(total, dbCrossrefJson);
    }

    public List<Pair<String, String>> retrieveList(Integer total, Dbi<ByteBuffer> db) {
        if (total == null || total == 0) {
            total = DEFAULT_MAX_SIZE_LIST;
        }

        List<Pair<String, String>> values = new ArrayList<>();
        int counter = 0;

        try (Txn<ByteBuffer> txn = environment.txnRead()) {
            try (CursorIterator<ByteBuffer> it = db.iterate(txn, KeyRange.all())) {
                for (final CursorIterator.KeyVal<ByteBuffer> kv : it.iterable()) {
                    String key = null;
                    try {
                        key = (String) BinarySerialiser.deserialize(kv.key());
                        values.add(new ImmutablePair<>(key, (String) BinarySerialiser.deserializeAndDecompress(kv.val())));
                    } catch (IOException e) {
                        LOGGER.error("Cannot decompress document with key: " + key, e);
                    }
                    if (counter == total) {
                        txn.close();
                        break;
                    }
                    counter++;
                }
            }
        }

        return values;
    }
}
