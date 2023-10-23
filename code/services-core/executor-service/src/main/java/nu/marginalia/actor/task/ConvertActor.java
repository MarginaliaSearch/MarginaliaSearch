package nu.marginalia.actor.task;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.actor.prototype.RecordActorPrototype;
import nu.marginalia.actor.state.ActorResumeBehavior;
import nu.marginalia.actor.state.ActorStep;
import nu.marginalia.actor.state.Resume;
import nu.marginalia.process.ProcessOutboxes;
import nu.marginalia.process.ProcessService;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorageBaseType;
import nu.marginalia.storage.model.FileStorageId;
import nu.marginalia.storage.model.FileStorageState;
import nu.marginalia.storage.model.FileStorageType;
import nu.marginalia.mq.MqMessageState;
import nu.marginalia.mq.outbox.MqOutbox;
import nu.marginalia.mqapi.converting.ConvertAction;
import nu.marginalia.mqapi.converting.ConvertRequest;

import java.nio.file.Files;
import java.nio.file.Path;

@Singleton
public class ConvertActor extends RecordActorPrototype {
    private final ActorProcessWatcher processWatcher;
    private final MqOutbox mqConverterOutbox;
    private final FileStorageService storageService;
    private final Gson gson;

    public record Convert(FileStorageId fid) implements ActorStep {};
    public record ConvertEncyclopedia(String source) implements ActorStep {};
    public record ConvertDirtree(String source) implements ActorStep {};
    public record ConvertStackexchange(String source) implements ActorStep {};
    @Resume(behavior = ActorResumeBehavior.RETRY)
    public record ConvertWait(FileStorageId destFid,
                              long msgId) implements ActorStep {};

    @Override
    public ActorStep transition(ActorStep self) throws Exception {
        return switch (self) {
            case Convert (FileStorageId fid) -> {
                var toProcess = storageService.getStorage(fid);
                var base = storageService.getStorageBase(FileStorageBaseType.STORAGE);
                var processedArea = storageService.allocateTemporaryStorage(base,
                        FileStorageType.PROCESSED_DATA, "processed-data",
                        "Processed Data; " + toProcess.description());

                storageService.relateFileStorages(toProcess.id(), processedArea.id());
                storageService.setFileStorageState(processedArea.id(), FileStorageState.NEW);

                // Pre-send convert request
                var request = new ConvertRequest(ConvertAction.ConvertCrawlData,
                        null,
                        fid,
                        processedArea.id());

                yield new ConvertWait(
                        processedArea.id(),
                        mqConverterOutbox.sendAsync(ConvertRequest.class.getSimpleName(), gson.toJson(request))
                );
            }
            case ConvertDirtree(String source) -> {
                Path sourcePath = Path.of(source);
                if (!Files.exists(sourcePath))
                    yield new Error("Source path does not exist: " + sourcePath);

                String fileName = sourcePath.toFile().getName();

                var base = storageService.getStorageBase(FileStorageBaseType.STORAGE);
                var processedArea = storageService.allocateTemporaryStorage(base,
                        FileStorageType.PROCESSED_DATA, "processed-data",
                        "Processed Dirtree Data; " + fileName);

                storageService.setFileStorageState(processedArea.id(), FileStorageState.NEW);

                // Pre-send convert request
                var request = new ConvertRequest(ConvertAction.SideloadDirtree,
                        sourcePath.toString(),
                        null,
                        processedArea.id());

                yield new ConvertWait(
                        processedArea.id(),
                        mqConverterOutbox.sendAsync(ConvertRequest.class.getSimpleName(), gson.toJson(request))
                );
            }
            case ConvertEncyclopedia(String source) -> {

                Path sourcePath = Path.of(source);
                if (!Files.exists(sourcePath))
                    yield new Error("Source path does not exist: " + sourcePath);

                String fileName = sourcePath.toFile().getName();

                var base = storageService.getStorageBase(FileStorageBaseType.STORAGE);
                var processedArea = storageService.allocateTemporaryStorage(base,
                        FileStorageType.PROCESSED_DATA, "processed-data",
                        "Processed Encylopedia Data; " + fileName);

                storageService.setFileStorageState(processedArea.id(), FileStorageState.NEW);

                // Pre-send convert request
                var request = new ConvertRequest(ConvertAction.SideloadEncyclopedia,
                        sourcePath.toString(),
                        null,
                        processedArea.id());


                yield new ConvertWait(
                        processedArea.id(),
                        mqConverterOutbox.sendAsync(ConvertRequest.class.getSimpleName(), gson.toJson(request))
                );
            }
            case ConvertStackexchange(String source) -> {

                Path sourcePath = Path.of(source);
                if (!Files.exists(sourcePath))
                    yield new Error("Source path does not exist: " + sourcePath);

                String fileName = sourcePath.toFile().getName();

                var base = storageService.getStorageBase(FileStorageBaseType.STORAGE);
                var processedArea = storageService.allocateTemporaryStorage(base,
                        FileStorageType.PROCESSED_DATA, "processed-data",
                        "Processed Stackexchange Data; " + fileName);

                storageService.setFileStorageState(processedArea.id(), FileStorageState.NEW);

                // Pre-send convert request
                var request = new ConvertRequest(ConvertAction.SideloadStackexchange,
                        sourcePath.toString(),
                        null,
                        processedArea.id());

                yield new ConvertWait(
                        processedArea.id(),
                        mqConverterOutbox.sendAsync(ConvertRequest.class.getSimpleName(), gson.toJson(request))
                );
            }
            case ConvertWait(FileStorageId destFid, long msgId) -> {
                var rsp = processWatcher.waitResponse(mqConverterOutbox, ProcessService.ProcessId.CONVERTER, msgId);

                if (rsp.state() != MqMessageState.OK) {
                    yield new Error("Converter failed");
                }

                storageService.setFileStorageState(destFid, FileStorageState.UNSET);
                yield new End();
            }
            default -> new Error();
        };
    }

    @Override
    public String describe() {
        return "Convert a set of crawl data into a format suitable for loading into the database.";
    }

    @Inject
    public ConvertActor(ActorProcessWatcher processWatcher,
                        ProcessOutboxes processOutboxes,
                        FileStorageService storageService,
                        Gson gson)
    {
        super(gson);
        this.processWatcher = processWatcher;
        this.mqConverterOutbox = processOutboxes.getConverterOutbox();
        this.storageService = storageService;
        this.gson = gson;
    }
}
