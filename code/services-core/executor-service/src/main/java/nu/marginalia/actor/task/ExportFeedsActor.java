package nu.marginalia.actor.task;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.actor.prototype.RecordActorPrototype;
import nu.marginalia.actor.state.ActorStep;
import nu.marginalia.extractor.ExporterIf;
import nu.marginalia.extractor.FeedExporter;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorageId;
import nu.marginalia.storage.model.FileStorageState;
import nu.marginalia.storage.model.FileStorageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

@Singleton
public class ExportFeedsActor extends RecordActorPrototype {
    private final FileStorageService storageService;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ExporterIf feedExporter;
    public record Export(FileStorageId crawlId) implements ActorStep {}
    public record Run(FileStorageId crawlId, FileStorageId destId) implements ActorStep {}
    @Override
    public ActorStep transition(ActorStep self) throws Exception {
        return switch(self) {
            case Export(FileStorageId crawlId) -> {
                var storage = storageService.allocateStorage(FileStorageType.EXPORT, "feed-export", "Feeds " + LocalDateTime.now());

                if (storage == null) yield new Error("Bad storage id");
                yield new Run(crawlId, storage.id());
            }
            case Run(FileStorageId crawlId, FileStorageId destId) -> {
                storageService.setFileStorageState(destId, FileStorageState.NEW);

                try {
                    feedExporter.export(crawlId, destId);
                    storageService.setFileStorageState(destId, FileStorageState.UNSET);
                }
                catch (Exception ex) {
                    storageService.setFileStorageState(destId, FileStorageState.DELETE);
                    yield new Error("Failed to export data");
                }

                yield new End();
            }
            default -> new Error();
        };
    }


    @Override
    public String describe() {
        return "Export RSS/Atom feeds from crawl data";
    }

    @Inject
    public ExportFeedsActor(Gson gson,
                            FileStorageService storageService,
                            FeedExporter feedExporter)
    {
        super(gson);
        this.storageService = storageService;
        this.feedExporter = feedExporter;
    }

}
