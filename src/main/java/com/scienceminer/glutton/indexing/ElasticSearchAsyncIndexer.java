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
import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
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

public class ElasticSearchAsyncIndexer {
    protected static final Logger logger = LoggerFactory.getLogger(ElasticSearchAsyncIndexer.class);

    private static volatile ElasticSearchAsyncIndexer instance;

    private LookupConfiguration configuration;
    private RestClient restClient;
    private ElasticsearchTransport transport;
    private ElasticsearchAsyncClient elasticsearchAsyncClient;

    private String settingsPath = "config/elastic-settings.json";

    public static ElasticSearchAsyncIndexer getInstance(LookupConfiguration configuration) {
        if (instance == null) {
            synchronized (ElasticSearchAsyncIndexer.class) {
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
        instance = new ElasticSearchAsyncIndexer(configuration);
    }

    private ElasticSearchAsyncIndexer(LookupConfiguration configuration) {
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
        elasticsearchAsyncClient = new ElasticsearchAsyncClient(transport);
    }

    /**
     * Extend an existing index with a set of documents. The expected values are JSON 
     * documents in the CrossRef format. 
     * 
     * Asynchronous version useful when combining processes of storing and indexing.
     * 
     * Document identifier is the source prefixed by the main identifier of the this source: 
     * e.g. crossref:DOI, hal:HalID, pubmed:pmid 
     * 
     * Already existing keys are skipt if update is false.
     * 
     **/
    public void asyncIndexDocuments(List<String> documents, boolean update, Counter counterIndexedRecords, Counter counterFailedIndexedRecords) {
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

            if (objToIndex == null) {
                // counter here for records that failed to index
                counterFailedIndexedRecords.inc();
            }
        }

        try {
            //BulkResponse result = this.elasticsearchClient.bulk(br.build()); 
            this.elasticsearchAsyncClient.bulk(
                br.build()
            ).whenComplete((response, exception) -> {
                if (exception != null) {
                    logger.error("Batch indexing failed", exception);
                } else {
                    counterIndexedRecords.inc(documents.size());
                }

                if (response.errors()) {
                    logger.error("Bulk had errors");
                    for (BulkResponseItem item: response.items()) {
                        if (item.error() != null) {
                            logger.error(item.error().reason());
                        }
                    }
                }
            });
            
        } catch (Exception e) {
            logger.error("Batch indexing failed", e);
        }
    }

}
