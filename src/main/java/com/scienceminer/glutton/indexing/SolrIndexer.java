package com.scienceminer.glutton.indexing;

import java.io.IOException;
import java.util.*;

import com.scienceminer.glutton.configuration.LookupConfiguration;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.common.SolrInputDocument;

import org.lmdbjava.*;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SolrIndexer {
    protected static final Logger logger = LoggerFactory.getLogger(SolrIndexer.class);

    private static volatile SolrIndexer instance;

    private LookupConfiguration configuration;

    
}
