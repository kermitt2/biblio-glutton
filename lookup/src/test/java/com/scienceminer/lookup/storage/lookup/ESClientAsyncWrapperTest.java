package com.scienceminer.lookup.storage.lookup;

import com.scienceminer.lookup.storage.lookup.async.ESClientWrapper;
import org.apache.http.HttpHost;
import org.easymock.EasyMock;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;

import static com.scienceminer.lookup.storage.lookup.MetadataMatching.INDEX_FIELD_NAME_BIBLIOGRAPHIC;
import static org.assertj.core.internal.bytebuddy.matcher.ElementMatchers.is;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.assertThat;

public class ESClientAsyncWrapperTest {

    private ESClientWrapper target;
    private RestHighLevelClient mockEsClient;
    private RestHighLevelClient esClient;

    @Before
    public void setUp() throws Exception {
        mockEsClient = EasyMock.createNiceMock(RestHighLevelClient.class);
        esClient = new RestHighLevelClient(
                RestClient.builder(
                        HttpHost.create("localhost:9200"))
                        .setRequestConfigCallback(
                                requestConfigBuilder -> requestConfigBuilder
                                        .setConnectTimeout(30000)
                                        .setSocketTimeout(60000))
                        .setMaxRetryTimeoutMillis(120000));

        target = new ESClientWrapper(esClient, 2048);
    }

    @Test
    public void testABC() throws Exception {
        String biblio = "G. L. Peterson. A new solution to Lamport's concurrent programming problem using small shared variables.  ACM Transactions on Programming Languages Systems,  5(1):56-65,  1983.";

        final MatchQueryBuilder query = QueryBuilders.matchQuery(INDEX_FIELD_NAME_BIBLIOGRAPHIC, biblio);

        SearchSourceBuilder builder = new SearchSourceBuilder();
        builder.query(query);
        builder.from(0);
        builder.size(1);

        final SearchRequest searchRequest = new SearchRequest("crossref_light");
        searchRequest.searchType(SearchType.DFS_QUERY_THEN_FETCH);
        searchRequest.source(builder);

//        final SearchRequest fakeSearchRequest = new SearchRequest("crossref_light");
//        expect(mockEsClient.search(EasyMock.anyObject(), RequestOptions.DEFAULT)).andReturn(new SearchResponse());
//        replay(mockEsClient);

//        final CompletableFuture<SearchResponse> searchResponseCompletableFuture = target.searchAsync(searchRequest, RequestOptions.DEFAULT,
//                searchResponse -> System.out.println(searchRequest.toString()));

//        System.out.println(searchResponseCompletableFuture.get());


//        verify(mockEsClient);
    }
}