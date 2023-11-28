package nu.marginalia.control.actor.precession;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.actor.prototype.RecordActorPrototype;
import nu.marginalia.actor.state.ActorResumeBehavior;
import nu.marginalia.actor.state.ActorStep;
import nu.marginalia.actor.state.Resume;
import nu.marginalia.executor.client.ExecutorRemoteActorFactory;
import nu.marginalia.nodecfg.NodeConfigurationService;
import nu.marginalia.nodecfg.model.NodeConfiguration;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorageType;

import java.sql.SQLException;
import java.util.OptionalInt;
import java.util.concurrent.TimeUnit;

@Singleton
public class ReprocessAllActor extends RecordActorPrototype {
    private final ExecutorRemoteActorFactory remoteActorFactory;

    private final FileStorageService fileStorageService;
    private final NodeConfigurationService nodeConfigurationService;


    public record Initial() implements ActorStep {}

    public record WaitFinished(int node) implements ActorStep {}
    @Resume(behavior=ActorResumeBehavior.RETRY)
    public record Trigger(int node) implements ActorStep {}
    public record AdvanceNode(int node) implements ActorStep {}


    @Override
    public ActorStep transition(ActorStep self) throws Exception {
        PrecessionNodes precessionNodes = new PrecessionNodes();

        return switch (self) {
            case Initial i -> {
                var id = precessionNodes.first();
                if (id.isPresent()) {
                    yield new Trigger(id.getAsInt());
                }
                else {
                    yield new End();
                }
            }
            case Trigger(int node) -> {
                var activeFileStorage = fileStorageService.getActiveFileStorages(node, FileStorageType.CRAWL_DATA);
                if (activeFileStorage.size() != 1) {
                    yield new AdvanceNode(node);
                }

                var data = new ExecutorRemoteActorFactory.ConvertAndLoadData(activeFileStorage.get(0));

                if (remoteActorFactory.createConvertAndLoadRemote(node).trigger(data)) {
                    yield new WaitFinished(node);
                }
                else {
                    yield new AdvanceNode(node);
                }
            }
            case WaitFinished(int node) -> {
                var remoteActor = remoteActorFactory.createConvertAndLoadRemote(node);
                for (;;) {
                    var state = remoteActor.getState();
                    if ("END".equals(state) || "ERROR".equals(state))
                        break;
                    TimeUnit.SECONDS.sleep(10);
                }
                yield new AdvanceNode(node);
            }
            case AdvanceNode(int node) -> {
                var id = precessionNodes.next(node);

                if (id.isPresent())
                    yield new Trigger(id.getAsInt());
                else
                    yield new End();
            }
            default -> new Error();
        };
    }

    @Inject
    public ReprocessAllActor(Gson gson,
                             ExecutorRemoteActorFactory remoteActorFactory,
                             FileStorageService fileStorageService,
                             NodeConfigurationService nodeConfigurationService)
    {
        super(gson);
        this.remoteActorFactory = remoteActorFactory;
        this.fileStorageService = fileStorageService;
        this.nodeConfigurationService = nodeConfigurationService;
    }

    @Override
    public String describe() {
        return "Triggers a cascade of reindex instructions across each node included in the precession";
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
