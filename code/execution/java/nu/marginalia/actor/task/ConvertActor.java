package nu.marginalia.actor.task;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.actor.prototype.RecordActorPrototype;
import nu.marginalia.actor.state.ActorResumeBehavior;
import nu.marginalia.actor.state.ActorStep;
import nu.marginalia.actor.state.Resume;
import nu.marginalia.encyclopedia.EncyclopediaConverter;
import nu.marginalia.process.ProcessOutboxes;
import nu.marginalia.process.ProcessService;
import nu.marginalia.sideload.RedditSideloadHelper;
import nu.marginalia.sideload.SideloadHelper;
import nu.marginalia.sideload.StackExchangeSideloadHelper;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorageId;
import nu.marginalia.storage.model.FileStorageState;
import nu.marginalia.storage.model.FileStorageType;
import nu.marginalia.mq.MqMessageState;
import nu.marginalia.mq.outbox.MqOutbox;
import nu.marginalia.mqapi.converting.ConvertRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

@Singleton
public class ConvertActor extends RecordActorPrototype {

    private static final Logger logger = LoggerFactory.getLogger(ConvertActor.class);
    private final ActorProcessWatcher processWatcher;
    private final MqOutbox mqConverterOutbox;
    private final FileStorageService storageService;

    public record Convert(FileStorageId fid) implements ActorStep {}

    public record ConvertEncyclopedia(String source, String baseUrl) implements ActorStep {}

    public record PredigestEncyclopedia(String source, String dest, String baseUrl) implements ActorStep {}

    public record ConvertDirtree(String source) implements ActorStep {}

    public record ConvertWarc(String source) implements ActorStep {}

    public record ConvertReddit(String source) implements ActorStep {}

    public record ConvertStackexchange(String source) implements ActorStep {}

    @Resume(behavior = ActorResumeBehavior.RETRY)
    public record ConvertWait(FileStorageId destFid,
                              long msgId) implements ActorStep {}

    @Override
    public ActorStep transition(ActorStep self) throws Exception {
        return switch (self) {
            case Convert (FileStorageId fid) -> {
                var toProcess = storageService.getStorage(fid);
                var processedArea = storageService.allocateStorage(
                        FileStorageType.PROCESSED_DATA, "processed-data",
                        "Processed Data; " + toProcess.description());

                storageService.relateFileStorages(toProcess.id(), processedArea.id());
                storageService.setFileStorageState(processedArea.id(), FileStorageState.NEW);

                yield new ConvertWait(
                        processedArea.id(),
                        mqConverterOutbox.sendAsync(ConvertRequest.forCrawlData(fid, processedArea.id()))
                );
            }
            case ConvertDirtree(String source) -> {
                Path sourcePath = Path.of(source);
                if (!Files.exists(sourcePath))
                    yield new Error("Source path does not exist: " + sourcePath);

                String fileName = sourcePath.toFile().getName();

                var processedArea = storageService.allocateStorage(
                        FileStorageType.PROCESSED_DATA, "processed-data",
                        "Processed Dirtree Data; " + fileName);

                storageService.setFileStorageState(processedArea.id(), FileStorageState.NEW);

                yield new ConvertWait(
                        processedArea.id(),
                        mqConverterOutbox.sendAsync(ConvertRequest.forDirtree(sourcePath, processedArea.id()))
                );
            }
            case ConvertWarc(String source) -> {
                Path sourcePath = Path.of(source);
                if (!Files.exists(sourcePath))
                    yield new Error("Source path does not exist: " + sourcePath);

                String fileName = sourcePath.toFile().getName();

                var processedArea = storageService.allocateStorage(
                        FileStorageType.PROCESSED_DATA, "processed-data",
                        "Processed Warc Data; " + fileName);

                storageService.setFileStorageState(processedArea.id(), FileStorageState.NEW);

                yield new ConvertWait(
                        processedArea.id(),
                        mqConverterOutbox.sendAsync(ConvertRequest.forWarc(sourcePath, processedArea.id()))
                );
            }
            case ConvertReddit(String source) -> {
                Path sourcePath = Path.of(source);
                if (!Files.exists(sourcePath))
                    yield new Error("Source path does not exist: " + sourcePath);

                String fileName = sourcePath.toFile().getName();

                var processedArea = storageService.allocateStorage(
                        FileStorageType.PROCESSED_DATA, "processed-data",
                        "Processed Reddit Data; " + fileName);

                storageService.setFileStorageState(processedArea.id(), FileStorageState.NEW);

                // Convert reddit data to sqlite database
                // (we can't use a Predigest- step here because the conversion is too complicated)
                RedditSideloadHelper.convertRedditData(sourcePath);

                yield new ConvertWait(
                        processedArea.id(),
                        mqConverterOutbox.sendAsync(ConvertRequest.forReddit(sourcePath, processedArea.id()))
                );
            }
            case ConvertEncyclopedia(String source, String baseUrl) -> {

                Path sourcePath = Path.of(source);
                if (!Files.exists(sourcePath))
                    yield new Error("Source path does not exist: " + sourcePath);

                if (source.toLowerCase().endsWith(".zim")) {
                    // If we're fed a ZIM file, we need to convert it to a sqlite database first
                    String hash = SideloadHelper.getCrc32FileHash(sourcePath);

                    // To avoid re-converting the same file, we'll assign the file a name based on its hash
                    // and the original filename. This way, if we're fed the same file again, we'll be able to just
                    // re-use the predigested database file.
                    yield new PredigestEncyclopedia(source, STR."\{source}.\{hash}.db", baseUrl);
                } else if (!source.endsWith(".db")) {
                    yield new Error("Source path must be a ZIM or pre-digested sqlite database file (.db)");
                }


                String fileName = sourcePath.toFile().getName();

                var processedArea = storageService.allocateStorage(
                        FileStorageType.PROCESSED_DATA, "processed-data",
                        "Processed Encylopedia Data; " + fileName);

                storageService.setFileStorageState(processedArea.id(), FileStorageState.NEW);

                yield new ConvertWait(
                        processedArea.id(),
                        mqConverterOutbox.sendAsync(ConvertRequest.forEncyclopedia(sourcePath, baseUrl, processedArea.id()))
                );
            }
            case PredigestEncyclopedia(String source, String dest, String baseUrl) -> {
                Path sourcePath = Path.of(source);

                if (!Files.exists(sourcePath)) {
                    yield new Error("Source path does not exist: " + sourcePath);
                }

                Path destPath = Path.of(dest);
                if (Files.exists(destPath)) {
                    // Already predigested, go straight to convert step
                    yield new ConvertEncyclopedia(dest, baseUrl);
                }

                Path tempFile = Files.createTempFile(destPath.getParent(), "encyclopedia", "db.tmp");

                try {
                    EncyclopediaConverter.convert(sourcePath, tempFile);
                    Files.move(tempFile, destPath);
                }
                catch (Exception e) {
                    logger.error("Failed to convert ZIM file to sqlite database", e);
                    Files.deleteIfExists(tempFile);
                    Files.deleteIfExists(destPath);

                    yield new Error("Failed to convert ZIM file to sqlite database: " + e.getMessage());
                }

                // Go back to convert step with the new database file
                yield new ConvertEncyclopedia(dest, baseUrl);
            }
            case ConvertStackexchange(String source) -> {

                Path sourcePath = Path.of(source);
                if (!Files.exists(sourcePath))
                    yield new Error("Source path does not exist: " + sourcePath);

                String fileName = sourcePath.toFile().getName();

                var processedArea = storageService.allocateStorage(
                        FileStorageType.PROCESSED_DATA, "processed-data",
                        "Processed Stackexchange Data; " + fileName);

                storageService.setFileStorageState(processedArea.id(), FileStorageState.NEW);

                // Convert stackexchange data to sqlite database
                // (we can't use a Predigest- step here because the conversion is too complicated)
                var preprocessedPath = StackExchangeSideloadHelper.convertStackexchangeData(sourcePath);

                if (preprocessedPath.isEmpty())
                    yield new Error("Failed to convert stackexchange 7z file to sqlite database");

                // Pre-send convert request

                yield new ConvertWait(
                        processedArea.id(),
                        mqConverterOutbox.sendAsync(ConvertRequest.forStackexchange(preprocessedPath.get(), processedArea.id()))
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
    }
}
