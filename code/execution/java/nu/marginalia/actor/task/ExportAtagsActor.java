package nu.marginalia.actor.task;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.extractor.AtagExporter;
import nu.marginalia.extractor.ExporterIf;
import nu.marginalia.storage.model.*;
import nu.marginalia.actor.prototype.RecordActorPrototype;
import nu.marginalia.actor.state.ActorStep;
import nu.marginalia.storage.FileStorageService;

import java.time.LocalDateTime;

@Singleton
public class ExportAtagsActor extends RecordActorPrototype {
    private final FileStorageService storageService;
    private final ExporterIf atagExporter;

    public record Export(FileStorageId crawlId) implements ActorStep {}
    public record Run(FileStorageId crawlId, FileStorageId destId) implements ActorStep {}
    @Override
    public ActorStep transition(ActorStep self) throws Exception {
        return switch(self) {
            case Export(FileStorageId crawlId) -> {
                var storage = storageService.allocateStorage(FileStorageType.EXPORT, "atag-export", "Anchor Tags " + LocalDateTime.now());

                if (storage == null) yield new Error("Bad storage id");
                yield new Run(crawlId, storage.id());
            }
            case Run(FileStorageId crawlId, FileStorageId destId) -> {
                storageService.setFileStorageState(destId, FileStorageState.NEW);

                try {
                    atagExporter.export(crawlId, destId);
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
        return "Export anchor tags from crawl data";
    }

    @Inject
    public ExportAtagsActor(Gson gson,
                            FileStorageService storageService,
                            AtagExporter atagExporter)
    {
        super(gson);
        this.storageService = storageService;
        this.atagExporter = atagExporter;
    }

}
