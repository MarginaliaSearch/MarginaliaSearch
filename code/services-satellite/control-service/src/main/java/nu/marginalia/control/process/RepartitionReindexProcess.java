package nu.marginalia.control.process;

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
public class RepartitionReindexProcess extends AbstractStateGraph {

    private final MqOutbox indexOutbox;

    // STATES

    private static final String INITIAL = "INITIAL";
    private static final String REPARTITION = "REPARTITION";
    private static final String REPARTITION_REPLY = "REPARTITION-REPLY";
    private static final String REINDEX = "REINDEX";
    private static final String REINDEX_REPLY = "REINDEX-REPLY";
    private static final String END = "END";


    @Inject
    public RepartitionReindexProcess(StateFactory stateFactory,
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

    @GraphState(name = REPARTITION, next = REPARTITION_REPLY)
    public Long repartition() throws Exception {
        return indexOutbox.sendAsync(IndexMqEndpoints.INDEX_REPARTITION, "");
    }

    @GraphState(name = REPARTITION_REPLY, next = REINDEX, resume = ResumeBehavior.RETRY)
    public void repartitionReply(Long id) throws Exception {
        var rsp = indexOutbox.waitResponse(id);

        if (rsp.state() != MqMessageState.OK) {
            error("Repartition failed");
        }
    }

    @GraphState(name = REINDEX, next = REINDEX_REPLY)
    public Long reindex() throws Exception {
        return indexOutbox.sendAsync(IndexMqEndpoints.INDEX_REINDEX, "");
    }

    @GraphState(name = REINDEX_REPLY, next = END, resume = ResumeBehavior.RETRY)
    public void reindexReply(Long id) throws Exception {
        var rsp = indexOutbox.waitResponse(id);

        if (rsp.state() != MqMessageState.OK) {
            error("Repartition failed");
        }
    }

}
