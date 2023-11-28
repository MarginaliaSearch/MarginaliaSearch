package nu.marginalia.control.actor.precession;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.actor.prototype.RecordActorPrototype;
import nu.marginalia.actor.state.ActorResumeBehavior;
import nu.marginalia.actor.state.ActorStep;
import nu.marginalia.actor.state.Resume;
import nu.marginalia.index.client.IndexClient;
import nu.marginalia.mq.persistence.MqPersistence;
import nu.marginalia.nodecfg.NodeConfigurationService;
import nu.marginalia.nodecfg.model.NodeConfiguration;

import java.sql.SQLException;
import java.util.OptionalInt;
import java.util.concurrent.TimeUnit;

@Singleton
public class ReindexAllActor extends RecordActorPrototype {

    private final MqPersistence persistence;
    private final IndexClient indexClient;
    private final NodeConfigurationService nodeConfigurationService;


    public record Initial() implements ActorStep {}
    @Resume(behavior=ActorResumeBehavior.RETRY)
    public record ReindexNode(int node, long msgId) implements ActorStep {
        public ReindexNode(int node) { this(node, -1L); }

    }
    public record AdvanceNode(int node) implements ActorStep {}


    @Override
    public ActorStep transition(ActorStep self) throws Exception {
        PrecessionNodes precessionNodes = new PrecessionNodes();

        return switch (self) {
            case Initial i -> {
                var id = precessionNodes.first();
                if (id.isPresent()) {
                    yield new ReindexNode(id.getAsInt());
                }
                else {
                    yield new End();
                }
            }
            case ReindexNode(int node, long msgId) when msgId < 0 -> new ReindexNode(node, indexClient.triggerRepartition(node));
            case ReindexNode(int node, long msgId) -> {
                while (!isMessageTerminal(msgId)) {
                    TimeUnit.SECONDS.sleep(10);
                }

                yield new AdvanceNode(node);
            }
            case AdvanceNode(int node) -> {
                var id = precessionNodes.next(node);

                if (id.isPresent())
                    yield new ReindexNode(id.getAsInt());
                else
                    yield new End();
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
                           IndexClient indexClient, NodeConfigurationService nodeConfigurationService)
    {
        super(gson);
        this.persistence = persistence;
        this.indexClient = indexClient;
        this.nodeConfigurationService = nodeConfigurationService;
    }

    @Override
    public String describe() {
        return "Triggeres a cascade of reindex instructions across each node included in the precession";
    }

    private class PrecessionNodes {
        private final int[] nodes;

        private PrecessionNodes() throws SQLException {
            nodes = nodeConfigurationService.getAll().stream()
                    .filter(NodeConfiguration::includeInPrecession)
                    .mapToInt(NodeConfiguration::node)
                    .sorted()
                    .toArray();
        }

        public OptionalInt first() {
            if (nodes.length == 0)
                return OptionalInt.empty();
            else
                return OptionalInt.of(nodes[0]);
        }

        public OptionalInt next(int current) {
            for (int i = 0; i < nodes.length - 1 && nodes[i] <= current; i++) {
                if (nodes[i] == current) {
                    return OptionalInt.of(nodes[i + 1]);
                }
            }

            return OptionalInt.empty();
        }
    }
}
