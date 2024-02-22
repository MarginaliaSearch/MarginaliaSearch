package nu.marginalia.control.actor.precession;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.actor.prototype.RecordActorPrototype;
import nu.marginalia.actor.state.ActorResumeBehavior;
import nu.marginalia.actor.state.ActorStep;
import nu.marginalia.actor.state.Resume;
import nu.marginalia.control.actor.PrecessionNodes;
import nu.marginalia.index.api.IndexMqClient;
import nu.marginalia.mq.persistence.MqPersistence;

import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

@Singleton
public class ReindexAllActor extends RecordActorPrototype {

    private final MqPersistence persistence;
    private final IndexMqClient indexMqClient;
    private final PrecessionNodes precessionNodes;


    public record Initial() implements ActorStep {}

    @Resume(behavior=ActorResumeBehavior.RETRY)
    public record ReindexNode(int node, long msgId) implements ActorStep {
        public ReindexNode(int node) { this(node, -1L); }

    }
    public record AdvanceNode(int node) implements ActorStep {}


    @Override
    public ActorStep transition(ActorStep self) throws Exception {

        return switch (self) {
            case Initial i -> {
                var first = precessionNodes.first();

                if (first.isEmpty()) yield new End();
                else yield new ReindexNode(first.getAsInt());
            }
            case ReindexNode(int node, long msgId) when msgId < 0 -> new ReindexNode(node, indexMqClient.triggerRepartition(node));
            case ReindexNode(int node, long msgId) -> {
                while (!isMessageTerminal(msgId)) {
                    TimeUnit.SECONDS.sleep(10);
                }

                yield new AdvanceNode(node);
            }
            case AdvanceNode(int node) -> {
                var next = precessionNodes.next(node);
                if (next.isEmpty()) yield new End();
                else yield new ReindexNode(next.getAsInt());
            }
            default -> new Error();
        };
    }

    private boolean isMessageTerminal(long msgId) throws SQLException {
        return persistence.getMessage(msgId).state().isTerminal();
    }

    @Inject
    public ReindexAllActor(Gson gson,
                           MqPersistence persistence,
                           IndexMqClient indexMqClient,
                           PrecessionNodes precessionNodes)
    {
        super(gson);
        this.persistence = persistence;
        this.indexMqClient = indexMqClient;
        this.precessionNodes = precessionNodes;
    }

    @Override
    public String describe() {
        return "Triggeres a cascade of reindex instructions across each node included in the precession";
    }

}
