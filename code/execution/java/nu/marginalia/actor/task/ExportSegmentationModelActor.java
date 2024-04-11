package nu.marginalia.actor.task;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.actor.prototype.RecordActorPrototype;
import nu.marginalia.actor.state.ActorStep;
import nu.marginalia.segmentation.NgramExtractorMain;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.LocalDateTime;

@Singleton
public class ExportSegmentationModelActor extends RecordActorPrototype {

    private final FileStorageService storageService;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public record Export(String zimFile) implements ActorStep {}

    @Override
    public ActorStep transition(ActorStep self) throws Exception {
        return switch(self) {
            case Export(String zimFile) -> {

                var storage = storageService.allocateStorage(FileStorageType.EXPORT, "segmentation-model", "Segmentation Model Export " + LocalDateTime.now());

                Path countsFile = storage.asPath().resolve("ngram-counts.bin");

                NgramExtractorMain.dumpCounts(Path.of(zimFile), countsFile);

                yield new End();
            }
            default -> new Error();
        };
    }

    @Override
    public String describe() {
        return "Generate a query segmentation model from a ZIM file.";
    }

    @Inject
    public ExportSegmentationModelActor(Gson gson,
                                        FileStorageService storageService)
    {
        super(gson);
        this.storageService = storageService;
    }

}
