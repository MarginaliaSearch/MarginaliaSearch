package nu.marginalia.control.actor.precession;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.actor.prototype.RecordActorPrototype;
import nu.marginalia.actor.state.ActorResumeBehavior;
import nu.marginalia.actor.state.ActorStep;
import nu.marginalia.actor.state.Resume;
import nu.marginalia.control.actor.PrecessionNodes;
import nu.marginalia.executor.client.ExecutorRemoteActorFactory;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorageType;

import java.util.concurrent.TimeUnit;

@Singleton
public class RecrawlAllActor extends RecordActorPrototype {
    private final ExecutorRemoteActorFactory remoteActorFactory;

    private final FileStorageService fileStorageService;
    private final PrecessionNodes precessionNodes;


    public record Initial() implements ActorStep {}

    public record WaitFinished(int node, long msgId) implements ActorStep {}
    @Resume(behavior=ActorResumeBehavior.RETRY)
    public record Trigger(int node) implements ActorStep {}
    public record AdvanceNode(int node) implements ActorStep {}


    @Override
    public ActorStep transition(ActorStep self) throws Exception {

        return switch (self) {
            case Initial i -> {
                var first = precessionNodes.first();

                if (first.isEmpty()) yield new End();
                else yield new Trigger(first.getAsInt());
            }
            case Trigger(int node) -> {
                var activeFileStorage = fileStorageService.getActiveFileStorages(node, FileStorageType.CRAWL_DATA);
                if (activeFileStorage.size() != 1) {
                    yield new AdvanceNode(node);
                }

                var data = new ExecutorRemoteActorFactory.CrawlData(activeFileStorage.get(0), true);

                long msgId = remoteActorFactory.createCrawlRemote(node).trigger(data);
                if (msgId >= 0) {
                    yield new WaitFinished(node, msgId);
                }
                else {
                    yield new AdvanceNode(node);
                }
            }
            case WaitFinished(int node, long msgId) -> {
                var remoteActor = remoteActorFactory.createCrawlRemote(node);
                for (;;) {
                    var state = remoteActor.getState(msgId);
                    if ("END".equals(state) || "ERROR".equals(state)) {
                        break;
                    }
                    TimeUnit.SECONDS.sleep(10);
                }
                yield new AdvanceNode(node);
            }
            case AdvanceNode(int node) -> {
                var next = precessionNodes.next(node);
                if (next.isEmpty()) yield new End();
                else yield new Trigger(next.getAsInt());
            }
            default -> new Error();
        };
    }

    @Inject
    public RecrawlAllActor(Gson gson,
                           ExecutorRemoteActorFactory remoteActorFactory,
                           FileStorageService fileStorageService,
                           PrecessionNodes precessionNodes)
    {
        super(gson);
        this.remoteActorFactory = remoteActorFactory;
        this.fileStorageService = fileStorageService;
        this.precessionNodes = precessionNodes;
    }

    @Override
    public String describe() {
        return "Triggers a cascade of recrawl instructions across each node included in the precession";
    }

}
