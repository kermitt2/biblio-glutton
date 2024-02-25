package com.scienceminer.glutton.indexing;

import java.io.*;
import java.util.*;

import com.scienceminer.glutton.configuration.LookupConfiguration;

import co.elastic.clients.elasticsearch.*;
import co.elastic.clients.elasticsearch.cluster.*;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.transport.*;
import org.elasticsearch.client.RestClient;
import co.elastic.clients.elasticsearch.indices.*;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.endpoints.*;
import co.elastic.clients.elasticsearch.cat.IndicesResponse;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.RefreshRequest;
import co.elastic.clients.elasticsearch.core.bulk.*;

import com.scienceminer.glutton.exception.ServiceException;
import com.scienceminer.glutton.exception.ServiceOverloadedException;
import com.scienceminer.glutton.utils.BinarySerialiser;

import org.apache.http.HttpHost;
import org.apache.commons.io.FileUtils;

import com.codahale.metrics.Meter;
import com.codahale.metrics.Counter;

import org.lmdbjava.*;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElasticSearchIndexer {
    protected static final Logger logger = LoggerFactory.getLogger(ElasticSearchIndexer.class);

    private static volatile ElasticSearchIndexer instance;

    private LookupConfiguration configuration;
    private RestClient restClient;
    private ElasticsearchTransport transport;
    private ElasticsearchClient elasticsearchClient;

    private String settingsPath = "config/elastic-settings.json";

    public static ElasticSearchIndexer getInstance(LookupConfiguration configuration) {
        if (instance == null) {
            synchronized (ElasticSearchIndexer.class) {
                if (instance == null) {
                    getNewInstance(configuration);
                }
            }
        }
        return instance;
    }

    /**
     * Creates a new instance.
     */
    private static synchronized void getNewInstance(LookupConfiguration configuration) {
        instance = new ElasticSearchIndexer(configuration);
    }

    private ElasticSearchIndexer(LookupConfiguration configuration) {
        this.configuration = configuration;

        // Create the low-level client
        restClient = RestClient
            .builder(HttpHost.create(configuration.getElastic().getHost()))
            //.setDefaultHeaders(new Header[]{
            //    new BasicHeader("Authorization", "ApiKey " + apiKey)
            //})
            .build();

        // Create the transport with a Jackson mapper
        transport = new RestClientTransport(
            restClient, new JacksonJsonpMapper());

        // And create the API client
        elasticsearchClient = new ElasticsearchClient(transport);
    }

    public void healthCheck() {
        // view information about relevant indexes
        // The information includes the health status, running status, index names, index IDs, number of primary shards, and number of replica shards.
        try{
            IndicesResponse indicesResponse = elasticsearchClient.cat().indices();
            indicesResponse.valueBody().forEach(info -> System.out.println(info.index() + "\t" + info.health() + 
                "\t"+ info.status() + "\tid: " + info.uuid() +"\tprimary: " + info.pri() + "\treplica: " + info.rep()));
        } catch (IOException ioException) {
            logger.error("Health check status failed", ioException);
        }
    }

    public void deleteIndex(String indexName) {
        try {
            // Delete the index.
            DeleteIndexResponse deleteResponse = elasticsearchClient.indices().delete(createIndexBuilder -> createIndexBuilder
                    .index(indexName)
            );
            logger.info("Delete index successfully - " + deleteResponse.toString());
        } catch (IOException ioException) {
            logger.error("Delete index failed", ioException);
        }
    }    

    /**
     * Create the biblio-glutton search index and load the index settings and mapping
     **/
    public void createIndex(String indexName) {
        try {
            // settings and mappings
            String mappingJsonString = FileUtils.readFileToString(new File(settingsPath), "UTF-8");

            try (InputStream input = new FileInputStream(settingsPath)) {
                CreateIndexResponse createResponse  = this.elasticsearchClient
                    .indices().create(new CreateIndexRequest.Builder()
                    .withJson(input)
                    .index(indexName)
                    .build());
                logger.info("Create index successfully - \n" + createResponse.toString());
            } catch (IOException ioException) {
                logger.error("Create index failed", ioException);
            }
        } catch (IOException ioException) {
            logger.error("Reading ElasticSearch settings/mapping failed", ioException);
        }
    } 

    /**
     * Index all the values of a LMDB storage. The expected values are JSON documents
     * in the CrossRef format. 
     * Document identifier is the source prefixed by the main identifier of the this source: 
     * e.g. crossref:DOI, hal:HalID, pubmed:pmid 
     * 
     * Already existing keys are skipt if update is false.
     **/
    public void indexCollection(Env<ByteBuffer> environment, Dbi<ByteBuffer> jsonMetadataDb, boolean update, Meter meter, Counter counterIndexedRecords) {
        int counter = 0;
        long total = 0;

        try (final Txn<ByteBuffer> txn = environment.txnRead()) {
            total = jsonMetadataDb.stat(txn).entries;
        } catch (Env.ReadersFullException e) {
            throw new ServiceOverloadedException("Not enough readers for LMDB access, increase them or reduce the parallel request rate. ", e);
        }
//System.out.println("total: " + total);
        BulkRequest.Builder br = new BulkRequest.Builder();
        int bulkSize = 0;
        try (Txn<ByteBuffer> txn = environment.txnRead()) {
            try (CursorIterable<ByteBuffer> it = jsonMetadataDb.iterate(txn, KeyRange.all())) {
                for (final CursorIterable.KeyVal<ByteBuffer> kv : it) {
                    String key = null;
                    String value = null;
                    try {
                        key = (String) BinarySerialiser.deserialize(kv.key());
                        value = (String) BinarySerialiser.deserializeAndDecompress(kv.val());

                        MetadataObj objToIndex = MetadataObjBuilder.createMetadataObj(value);
                        if (objToIndex != null && !MetadataObjBuilder.isFilteredType(objToIndex)) {
                            objToIndex.type = null;
                            String localIdentifier = objToIndex._id;
                            objToIndex._id = null;

                            if (br == null) 
                                br = new BulkRequest.Builder();

                            br.operations(op -> op           
                                .index(idx -> idx            
                                    .index(configuration.getElastic().getIndex())       
                                    .id(localIdentifier)
                                    .document(objToIndex)
                                )
                            );
                            bulkSize++;
                        }

                        if (bulkSize == configuration.getIndexingBatchSize()) {
                            try {
                                BulkResponse result = this.elasticsearchClient.bulk(br.build());                            
                                if (result.errors()) {
                                    logger.error("Bulk had errors");
                                    for (BulkResponseItem item: result.items()) {
                                        if (item.error() != null) {
                                            logger.error(item.error().reason());
                                        }
                                    }
                                }
                                meter.mark(configuration.getIndexingBatchSize());
                                counterIndexedRecords.inc(configuration.getIndexingBatchSize());
                            } catch (IOException e) {
                                logger.error("Batch indexing failed", e);
                            }

                            br = new BulkRequest.Builder();
                            bulkSize = 0;
                        }
                    } catch (IOException e) {
                        logger.error("Cannot decompress document with key: " + key, e);
                    }
                    if (counter == total) {
                        txn.close();
                        break;
                    }
                    counter++;
                }
            }
        } catch (Env.ReadersFullException e) {
            throw new ServiceOverloadedException("Not enough readers for LMDB access, increase them or reduce the parallel request rate. ", e);
        }

        // last bulk, if not empty
        if (bulkSize > 0) {
            try {
                BulkResponse result = this.elasticsearchClient.bulk(br.build());                
                if (result.errors()) {
                    logger.error("Bulk had errors");
                    for (BulkResponseItem item: result.items()) {
                        if (item.error() != null) {
                            logger.error(item.error().reason());
                        }
                    }
                }
                meter.mark(bulkSize);
                counterIndexedRecords.inc(bulkSize);
            } catch (IOException e) {
                logger.error("Batch indexing failed", e);
            }
        }

        // as final step we refresh the index, something we have prevented before for speed reasons
        refreshIndex(configuration.getElastic().getIndex());
    }

    /**
     * Extend an existing index with a set of documents. The expected values are JSON 
     * documents in the CrossRef format. 
     * Document identifier is the source prefixed by the main identifier of the this source: 
     * e.g. crossref:DOI, hal:HalID, pubmed:pmid 
     * 
     * Already existing keys are skipt if update is false.
     * 
     **/
    public void indexDocuments(List<String> documents, boolean update, Counter counterIndexedRecords) {
        BulkRequest.Builder br = new BulkRequest.Builder();
        for(String document : documents) {
            MetadataObj objToIndex = MetadataObjBuilder.createMetadataObj(document);
            if (objToIndex != null && !MetadataObjBuilder.isFilteredType(objToIndex)) {
                objToIndex.type = null;
                String localIdentifier = objToIndex._id;
                objToIndex._id = null;
                br.operations(op -> op           
                    .index(idx -> idx            
                        .index(configuration.getElastic().getIndex())       
                        .id(localIdentifier)
                        .document(objToIndex)
                    )
                );
            }
        }

        try {
            BulkResponse result = this.elasticsearchClient.bulk(br.build());                            
            if (result.errors()) {
                logger.error("Bulk had errors");
                for (BulkResponseItem item: result.items()) {
                    if (item.error() != null) {
                        logger.error(item.error().reason());
                    }
                }
            }
            counterIndexedRecords.inc(documents.size());
        } catch (IOException e) {
            logger.error("Batch indexing failed", e);
        }
    }

    public void asyncIndexDocuments(List<String> documents, boolean update, Counter counterIndexedRecords) {
        BulkRequest.Builder br = new BulkRequest.Builder();
        for(String document : documents) {
            MetadataObj objToIndex = MetadataObjBuilder.createMetadataObj(document);
            if (objToIndex != null && !MetadataObjBuilder.isFilteredType(objToIndex)) {
                objToIndex.type = null;
                String localIdentifier = objToIndex._id;
                objToIndex._id = null;
                br.operations(op -> op           
                    .index(idx -> idx            
                        .index(configuration.getElastic().getIndex())       
                        .id(localIdentifier)
                        .document(objToIndex)
                    )
                );
            }
        }

        try {
            BulkResponse result = this.elasticsearchClient.bulk(br.build());                            
            if (result.errors()) {
                logger.error("Bulk had errors");
                for (BulkResponseItem item: result.items()) {
                    if (item.error() != null) {
                        logger.error(item.error().reason());
                    }
                }
            }
            counterIndexedRecords.inc(documents.size());
        } catch (IOException e) {
            logger.error("Batch indexing failed", e);
        }
    }

    public boolean indexExists(String indexName) {
        boolean result = false;
        try {
            BooleanResponse resultResponse = elasticsearchClient.indices().exists(ExistsRequest.of(e -> e.index(indexName)));
            result = resultResponse.value();
        } catch (IOException ioException) {
            logger.error("Exists index failed", ioException);
        }
        return result;
    }

    public void setupIndex(boolean extend) {
        healthCheck();
        if (!extend && indexExists(configuration.getElastic().getIndex())) {
            logger.warn("Deleting existing ElasicSearch index...");
            deleteIndex(configuration.getElastic().getIndex());
        }

        if (!indexExists(configuration.getElastic().getIndex())) {
            logger.warn("Creating new fresh ElasicSearch index...");
            createIndex(configuration.getElastic().getIndex());            
        }
    }

    public void refreshIndex(String indexName) {
        try {
            RefreshResponse resultResponse = elasticsearchClient.indices().refresh(RefreshRequest.of(e -> e.index(indexName)));
            //logger.info("Refresh existing ElasicSearch index...");
        } catch (IOException ioException) {
            logger.error("Refresh index failed", ioException);
        }
    }

}
