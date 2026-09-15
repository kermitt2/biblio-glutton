package com.scienceminer.glutton.storage.lookup;

/**
 * What the search index looks like from here, as found by one round trip to Elasticsearch.
 */
public final class ElasticsearchStatus {

    public enum State {
        /** Reachable, the index is there. */
        OK,
        /** No answer from the configured host. */
        UNREACHABLE,
        /** Reachable, but the configured index does not exist: every matching query would find nothing. */
        MISSING_INDEX,
        /** Reachable, but the check itself was refused. */
        ERROR
    }

    public final State state;
    public final String host;
    public final String index;
    /** Documents in the index, or -1 when it could not be counted. */
    public final long documents;
    /** What went wrong, null when nothing did. */
    public final String message;

    public ElasticsearchStatus(State state, String host, String index, long documents, String message) {
        this.state = state;
        this.host = host;
        this.index = index;
        this.documents = documents;
        this.message = message;
    }

    public static ElasticsearchStatus ok(String host, String index, long documents) {
        return new ElasticsearchStatus(State.OK, host, index, documents, null);
    }

    public static ElasticsearchStatus unreachable(String host, String index, String message) {
        return new ElasticsearchStatus(State.UNREACHABLE, host, index, -1, message);
    }

    public static ElasticsearchStatus missingIndex(String host, String index) {
        return new ElasticsearchStatus(State.MISSING_INDEX, host, index, -1,
                "The index '" + index + "' does not exist on " + host);
    }

    public static ElasticsearchStatus error(String host, String index, String message) {
        return new ElasticsearchStatus(State.ERROR, host, index, -1, message);
    }

    public boolean isOk() {
        return state == State.OK;
    }

    @Override
    public String toString() {
        return state + (message == null ? "" : ": " + message);
    }
}
