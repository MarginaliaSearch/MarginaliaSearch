package nu.marginalia.actor.precession;

import com.google.gson.Gson;
import com.google.inject.Inject;
import nu.marginalia.actor.prototype.RecordActorPrototype;
import nu.marginalia.actor.state.ActorStep;
import nu.marginalia.executor.client.ExecutorExportClient;
import nu.marginalia.mq.MqMessageState;
import nu.marginalia.mq.persistence.MqPersistence;
import nu.marginalia.nodecfg.NodeConfigurationService;
import nu.marginalia.nodecfg.model.NodeConfiguration;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorageType;

import java.time.Duration;
import java.util.Comparator;
import java.util.Optional;

public class ExportAllPrecessionActor extends RecordActorPrototype {

    private final NodeConfigurationService nodeConfigurationService;
    private final ExecutorExportClient exportClient;
    private final FileStorageService fileStorageService;
    private final MqPersistence persistence;

    @Inject
    public ExportAllPrecessionActor(Gson gson,
                                    NodeConfigurationService nodeConfigurationService,
                                    ExecutorExportClient exportClient,
                                    FileStorageService fileStorageService,
                                    MqPersistence persistence)
    {
        super(gson);
        this.nodeConfigurationService = nodeConfigurationService;
        this.exportClient = exportClient;
        this.fileStorageService = fileStorageService;
        this.persistence = persistence;
    }

    public enum ExportTask {
        FEEDS,
        ATAGS,
        TFREQ
    }

    public record Initial(ExportTask task) implements ActorStep {}
    public record Export(int nodeId, ExportTask task, long msgId) implements ActorStep {
        public Export(int nodeId, ExportTask task) {
            this(nodeId, task, -1);
        }
    }

    @Override
    public ActorStep transition(ActorStep self) throws Exception {
        return switch (self) {
            case Initial(ExportTask task) -> {
                var firstNode = nextNodeId(-1);
                if (firstNode.isEmpty())
                    yield new Error("No nodes included in precession");
                else
                    yield new Export(firstNode.get(), task);
            }

            case Export(int nodeId, ExportTask task, long msgId) when msgId < 0 -> {
                var activeStorages = fileStorageService.getActiveFileStorages(nodeId, FileStorageType.CRAWL_DATA);
                if (activeStorages.isEmpty()) {
                    yield new Error("Node " + nodeId + " has no active file storage");
                }
                var activeCrawlStorageId = activeStorages.getFirst();

                long trackingMsgId = switch(task) {
                    case ATAGS -> exportClient.exportAtags(nodeId, activeCrawlStorageId);
                    case TFREQ -> exportClient.exportTermFrequencies(nodeId, activeCrawlStorageId);
                    case FEEDS -> exportClient.exportRssFeeds(nodeId, activeCrawlStorageId);
                };

                yield new Export(nodeId, task, trackingMsgId);
            }

            case Export(int nodeId, ExportTask task, long msgId) -> {
                for (; ; ) {
                    var msg = persistence.getMessage(msgId);
                    if (!msg.state().isTerminal()) {
                        Thread.sleep(Duration.ofSeconds(30));
                        continue;
                    }
                    if (msg.state() == MqMessageState.OK) {
                        var nextNode = nextNodeId(nodeId);
                        if (nextNode.isEmpty()) {
                            yield new End();
                        } else {
                            yield new Export(nextNode.get(), task);
                        }
                    } else {
                        yield new Error("Export failed for node " + nodeId);
                    }
                }
            }
            default -> new Error("Unknown state");
        };
    }

    private Optional<Integer> nextNodeId(int currentNodeId) {
        return nodeConfigurationService.getAll()
                .stream().sorted(Comparator.comparing(NodeConfiguration::node))
                .filter(node -> node.node() > currentNodeId)
                .filter(NodeConfiguration::includeInPrecession)
                .map(NodeConfiguration::node)
                .findFirst();
    }

    @Override
    public String describe() {
        return "Runs an export job on each index node included in the precession";
    }
}
