package nu.marginalia.actor.task;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.actor.prototype.RecordActorPrototype;
import nu.marginalia.actor.state.ActorStep;
import nu.marginalia.extractor.ExporterIf;
import nu.marginalia.extractor.FeedExporter;
import nu.marginalia.extractor.SampleDataExporter;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorageBaseType;
import nu.marginalia.storage.model.FileStorageId;
import nu.marginalia.storage.model.FileStorageState;
import nu.marginalia.storage.model.FileStorageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

@Singleton
public class ExportSampleDataActor extends RecordActorPrototype {
    private final FileStorageService storageService;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final SampleDataExporter dataExporter;
    public record Export(FileStorageId crawlId, int size, String name) implements ActorStep {}
    public record Run(FileStorageId crawlId, FileStorageId destId, int size, String name) implements ActorStep {}
    @Override
    public ActorStep transition(ActorStep self) throws Exception {
        return switch(self) {
            case Export(FileStorageId crawlId, int size, String name) -> {
                var storageBase = storageService.getStorageBase(FileStorageBaseType.STORAGE);
                var storage = storageService.allocateTemporaryStorage(storageBase, FileStorageType.EXPORT,
                        "crawl-sample-export",
                        STR."Crawl Data Sample \{name}/\{size} \{LocalDateTime.now()}"
                );

                if (storage == null) yield new Error("Bad storage id");
                yield new Run(crawlId, storage.id(), size, name);
            }
            case Run(FileStorageId crawlId, FileStorageId destId, int size, String name) -> {
                storageService.setFileStorageState(destId, FileStorageState.NEW);

                try {
                    dataExporter.export(crawlId, destId, size, name);
                    storageService.setFileStorageState(destId, FileStorageState.UNSET);
                }
                catch (Exception ex) {
                    storageService.setFileStorageState(destId, FileStorageState.DELETE);

                    logger.error("Failed to export data", ex);

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
    public ExportSampleDataActor(Gson gson,
                                 FileStorageService storageService,
                                 SampleDataExporter dataExporter)
    {
        super(gson);
        this.storageService = storageService;
        this.dataExporter = dataExporter;
    }

}
