package nu.marginalia.control.fsm.task;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.index.client.IndexClient;
import nu.marginalia.index.client.IndexMqEndpoints;
import nu.marginalia.mq.MqMessageState;
import nu.marginalia.mq.outbox.MqOutbox;
import nu.marginalia.mqsm.StateFactory;
import nu.marginalia.mqsm.graph.AbstractStateGraph;
import nu.marginalia.mqsm.graph.GraphState;
import nu.marginalia.mqsm.graph.ResumeBehavior;

@Singleton
public class RepartitionReindexFSM extends AbstractStateGraph {

    private final MqOutbox indexOutbox;

    // STATES

    public static final String INITIAL = "INITIAL";
    public static final String REPARTITION = "REPARTITION";
    public static final String REPARTITION_WAIT = "REPARTITION-WAIT";
    public static final String REINDEX = "REINDEX";
    public static final String REINDEX_WAIT = "REINDEX-WAIT";
    public static final String END = "END";


    @Inject
    public RepartitionReindexFSM(StateFactory stateFactory,
                                 IndexClient indexClient) {
        super(stateFactory);

        indexOutbox = indexClient.outbox();
    }

    @GraphState(name = INITIAL, next = REPARTITION)
    public void init() throws Exception {
        var rsp = indexOutbox.send(IndexMqEndpoints.INDEX_IS_BLOCKED, "");

        if (rsp.payload().equalsIgnoreCase("true")) {
            error("Index is blocked");
        }
    }

    @GraphState(name = REPARTITION, next = REPARTITION_WAIT)
    public Long repartition() throws Exception {
        return indexOutbox.sendAsync(IndexMqEndpoints.INDEX_REPARTITION, "");
    }

    @GraphState(name = REPARTITION_WAIT, next = REINDEX, resume = ResumeBehavior.RETRY)
    public void repartitionReply(Long id) throws Exception {
        var rsp = indexOutbox.waitResponse(id);

        if (rsp.state() != MqMessageState.OK) {
            error("Repartition failed");
        }
    }

    @GraphState(name = REINDEX, next = REINDEX_WAIT)
    public Long reindex() throws Exception {
        return indexOutbox.sendAsync(IndexMqEndpoints.INDEX_REINDEX, "");
    }

    @GraphState(name = REINDEX_WAIT, next = END, resume = ResumeBehavior.RETRY)
    public void reindexReply(Long id) throws Exception {
        var rsp = indexOutbox.waitResponse(id);

        if (rsp.state() != MqMessageState.OK) {
            error("Repartition failed");
        }
    }

}
