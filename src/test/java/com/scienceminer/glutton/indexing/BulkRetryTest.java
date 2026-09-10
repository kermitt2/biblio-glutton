package com.scienceminer.glutton.indexing;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.ErrorCause;
import co.elastic.clients.elasticsearch._types.ErrorResponse;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.bulk.OperationType;
import com.scienceminer.glutton.indexing.BulkRetry.IndexOperation;
import com.scienceminer.glutton.indexing.BulkRetry.Outcome;
import org.junit.Test;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

/**
 * The retry logic of one bulk, driven with hand-made Elasticsearch answers so that no cluster is
 * needed. The pause between attempts is set to nothing.
 */
public class BulkRetryTest {

    private static List<IndexOperation> operations(String... ids) {
        List<IndexOperation> operations = new ArrayList<>();
        for (String id : ids) {
            operations.add(new IndexOperation(id, new MetadataObj()));
        }
        return operations;
    }

    private static List<IndexOperation> createOnly(String... ids) {
        List<IndexOperation> operations = new ArrayList<>();
        for (String id : ids) {
            operations.add(new IndexOperation(id, new MetadataObj(), true));
        }
        return operations;
    }

    private static List<String> ids(List<IndexOperation> operations) {
        return operations.stream().map(op -> op.id).collect(Collectors.toList());
    }

    private static BulkResponseItem acked(String id) {
        return BulkResponseItem.of(i -> i.index("glutton").id(id).status(201).operationType(OperationType.Index));
    }

    private static BulkResponseItem refused(String id, int status, String type) {
        return BulkResponseItem.of(i -> i.index("glutton").id(id).status(status).operationType(OperationType.Index)
                .error(ErrorCause.of(e -> e.type(type).reason("because"))));
    }

    private static BulkResponse response(BulkResponseItem... items) {
        boolean errors = Arrays.stream(items).anyMatch(item -> item.error() != null);
        return BulkResponse.of(b -> b.errors(errors).took(1).items(Arrays.asList(items)));
    }

    private static BulkResponse allAcked(List<IndexOperation> operations) {
        return response(operations.stream().map(op -> acked(op.id)).toArray(BulkResponseItem[]::new));
    }

    private static ElasticsearchException clusterError(int status) {
        return new ElasticsearchException("bulk", ErrorResponse.of(r -> r.status(status)
                .error(ErrorCause.of(e -> e.type("some_exception").reason("cluster said no")))));
    }

    /** Records what was sent on each attempt and answers from a script. */
    private static class ScriptedSender implements BulkRetry.BulkSender {
        final List<List<String>> sent = new ArrayList<>();
        private final List<Object> script;

        ScriptedSender(Object... script) {
            this.script = new ArrayList<>(Arrays.asList(script));
        }

        @Override
        public BulkResponse send(List<IndexOperation> operations) throws IOException {
            sent.add(ids(operations));
            Object next = script.isEmpty() ? "ack" : script.remove(0);
            if (next instanceof IOException) {
                throw (IOException) next;
            }
            if (next instanceof ElasticsearchException) {
                throw (ElasticsearchException) next;
            }
            if (next instanceof BulkResponse) {
                return (BulkResponse) next;
            }
            return allAcked(operations);
        }
    }

    private static Outcome index(ScriptedSender sender, List<IndexOperation> operations) throws InterruptedException {
        return new BulkRetry(sender, 0).index(operations);
    }

    @Test
    public void allAcknowledged_shouldBeSentOnce() throws Exception {
        ScriptedSender sender = new ScriptedSender();

        Outcome outcome = index(sender, operations("a", "b", "c"));

        assertThat(outcome.indexed, is(3));
        assertThat(outcome.failed, is(0));
        assertThat(sender.sent, hasSize(1));
    }

    @Test
    public void timeout_shouldSendTheWholeBulkAgain() throws Exception {
        ScriptedSender sender = new ScriptedSender(new SocketTimeoutException("30,000 milliseconds timeout"),
                new SocketTimeoutException("again"));

        Outcome outcome = index(sender, operations("a", "b"));

        assertThat(outcome.indexed, is(2));
        assertThat(outcome.failed, is(0));
        assertThat(sender.sent, hasSize(3));
        assertThat(sender.sent.get(2), contains("a", "b"));
    }

    @Test
    public void neverAnswering_shouldGiveUpAfterTheLastAttempt() throws Exception {
        Object[] script = new Object[BulkRetry.MAX_ATTEMPTS + 3];
        Arrays.fill(script, new IOException("connection refused"));
        ScriptedSender sender = new ScriptedSender(script);

        Outcome outcome = index(sender, operations("a", "b"));

        assertThat(outcome.indexed, is(0));
        // not refused, never taken: what a later run can still bring in
        assertThat(outcome.unsent, is(2));
        assertThat(outcome.failed, is(0));
        assertThat(sender.sent, hasSize(BulkRetry.MAX_ATTEMPTS));
    }

    @Test
    public void alreadyThere_shouldBeSkippedWhenNotToBeReplaced() throws Exception {
        ScriptedSender sender = new ScriptedSender(response(
                acked("a"),
                refused("b", 409, "version_conflict_engine_exception")));

        Outcome outcome = index(sender, createOnly("a", "b"));

        assertThat(outcome.indexed, is(1));
        assertThat(outcome.skipped, is(1));
        assertThat(outcome.failed, is(0));
        assertThat(sender.sent, hasSize(1));
    }

    @Test
    public void conflict_shouldStillBeAFailureWhenReplacing() throws Exception {
        // an index operation does not conflict; a 409 there is something else and is not hidden
        ScriptedSender sender = new ScriptedSender(response(
                refused("a", 409, "version_conflict_engine_exception")));

        Outcome outcome = index(sender, operations("a"));

        assertThat(outcome.skipped, is(0));
        assertThat(outcome.failed, is(1));
    }

    @Test
    public void rejectedItems_shouldBeTheOnlyOnesSentAgain() throws Exception {
        ScriptedSender sender = new ScriptedSender(response(
                acked("a"),
                refused("b", 429, "es_rejected_execution_exception"),
                acked("c"),
                refused("d", 429, "es_rejected_execution_exception")));

        Outcome outcome = index(sender, operations("a", "b", "c", "d"));

        assertThat(outcome.indexed, is(4));
        assertThat(outcome.failed, is(0));
        assertThat(sender.sent, hasSize(2));
        assertThat(sender.sent.get(1), contains("b", "d"));
    }

    @Test
    public void refusedForGood_shouldNotBeSentAgain() throws Exception {
        ScriptedSender sender = new ScriptedSender(response(
                acked("a"),
                refused("b", 400, "mapper_parsing_exception")));

        Outcome outcome = index(sender, operations("a", "b"));

        assertThat(outcome.indexed, is(1));
        assertThat(outcome.failed, is(1));
        assertThat(sender.sent, hasSize(1));
    }

    @Test
    public void busyCluster_shouldBeRetried() throws Exception {
        ScriptedSender sender = new ScriptedSender(clusterError(503), clusterError(429));

        Outcome outcome = index(sender, operations("a"));

        assertThat(outcome.indexed, is(1));
        assertThat(sender.sent, hasSize(3));
    }

    @Test
    public void badRequest_shouldFailTheBulkAtOnce() throws Exception {
        ScriptedSender sender = new ScriptedSender(clusterError(400));

        Outcome outcome = index(sender, operations("a", "b"));

        assertThat(outcome.indexed, is(0));
        // the cluster answered and said no: refused, not unsent
        assertThat(outcome.failed, is(2));
        assertThat(outcome.unsent, is(0));
        assertThat(sender.sent, hasSize(1));
    }

    @Test
    public void mismatchedAnswer_shouldSendTheBulkAgain() throws Exception {
        // one item for two documents: nothing can be paired, so both go again
        ScriptedSender sender = new ScriptedSender(response(acked("a")));

        Outcome outcome = index(sender, operations("a", "b"));

        assertThat(outcome.indexed, is(2));
        assertThat(sender.sent, hasSize(2));
        assertThat(sender.sent.get(1), contains("a", "b"));
    }
}
