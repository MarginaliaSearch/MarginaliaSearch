package nu.marginalia.actor.task;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.actor.prototype.RecordActorPrototype;
import nu.marginalia.actor.state.ActorResumeBehavior;
import nu.marginalia.actor.state.ActorStep;
import nu.marginalia.actor.state.Resume;
import nu.marginalia.service.control.ServiceEventLog;
import nu.marginalia.service.control.ServiceHeartbeat;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorage;
import nu.marginalia.storage.model.FileStorageId;
import nu.marginalia.storage.model.FileStorageState;
import nu.marginalia.storage.model.FileStorageType;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;

@Singleton
public class DownloadSampleActor extends RecordActorPrototype {

    private final FileStorageService storageService;
    private final ServiceEventLog eventLog;
    private final ServiceHeartbeat heartbeat;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Resume(behavior = ActorResumeBehavior.ERROR)
    public record Run(String setName) implements ActorStep {}
    @Resume(behavior = ActorResumeBehavior.RETRY)
    public record Download(FileStorageId fileStorageId, String url, String fileName) implements ActorStep {}
    @Resume(behavior = ActorResumeBehavior.ERROR)
    public record Extract(FileStorageId fileStorageId, String tarFile) implements ActorStep {}
    @Resume(behavior = ActorResumeBehavior.ERROR)
    public record SuccessCleanup(FileStorageId fileStorageId, String tarFile) implements ActorStep {}
    @Resume(behavior = ActorResumeBehavior.ERROR)
    public record ErrorCleanup(FileStorageId id) implements ActorStep {}

    @Override
    public ActorStep transition(ActorStep self) throws Exception {
        return switch(self) {
            case Run(String setName) -> {
                final FileStorage newStorage = storageService.allocateStorage(
                        FileStorageType.CRAWL_DATA,
                        "sample-crawl-data",
                        "Sample " + setName);

                storageService.setFileStorageState(newStorage.id(), FileStorageState.NEW);
                String downloadURI = getDownloadURL(setName).toString();
                String tarFileName = Files.createTempFile(newStorage.asPath(), "download", ".tar").toString();

                yield new Download(newStorage.id(), downloadURI, tarFileName);
            }
            case Download(FileStorageId fileStorageId, String downloadURI, String tarFileName) -> {

                eventLog.logEvent(DownloadSampleActor.class, "Downloading sample from " + downloadURI);

                Files.deleteIfExists(Path.of(tarFileName));

                HttpURLConnection urlConnection = (HttpURLConnection) new URI(downloadURI).toURL().openConnection();

                try (var hb = heartbeat.createServiceAdHocTaskHeartbeat("Downloading sample")) {
                    long size = urlConnection.getContentLengthLong();
                    byte[] buffer = new byte[8192];

                    try (var is = new BufferedInputStream(urlConnection.getInputStream());
                         var os = new BufferedOutputStream(Files.newOutputStream(Path.of(tarFileName), StandardOpenOption.CREATE))) {
                        long copiedSize = 0;

                        while (copiedSize < size) {
                            int read = is.read(buffer);

                            if (read < 0) // We've been promised a file of length 'size'
                                throw new IOException("Unexpected end of stream");

                            os.write(buffer, 0, read);
                            copiedSize += read;

                            // Update progress bar
                            hb.progress(String.format("%d MB", copiedSize / 1024 / 1024), (int) (copiedSize / 1024), (int) (size / 1024));
                        }
                    }

                }
                catch (Exception ex) {
                    eventLog.logEvent(DownloadSampleActor.class, "Error downloading sample");
                    logger.error("Error downloading sample", ex);
                    yield new Error();
                }
                finally {
                    urlConnection.disconnect();
                }

                eventLog.logEvent(DownloadSampleActor.class, "Download complete");
                yield new Extract(fileStorageId, tarFileName);
            }
            case Extract(FileStorageId fileStorageId, String tarFileName) -> {
                Path outputPath = storageService.getStorage(fileStorageId).asPath();

                eventLog.logEvent(getClass().getSimpleName(), "Extracting sample to " + outputPath);
                try (var tar = new TarArchiveInputStream(Files.newInputStream(Path.of(tarFileName)))) {
                    TarArchiveEntry nextEntry;
                    byte[] buffer = new byte[8192];

                    while ((nextEntry = tar.getNextEntry()) != null) {
                        // Poll for interruption, to ensure this can be cancelled
                        if (Thread.interrupted()) {
                            throw new InterruptedException();
                        }

                        if (nextEntry.isDirectory()) {
                            continue;
                        }

                        Path outputFile = outputPath.resolve(nextEntry.getName());
                        Files.createDirectories(outputFile.getParent(),
                                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwxr-xr-x"))
                        );

                        long size = nextEntry.getSize();

                        // Extract tar entry
                        try (var fos = Files.newOutputStream(outputFile, StandardOpenOption.CREATE)) {
                            transferBytes(tar, fos, buffer, size);
                        }

                        if (Files.isDirectory(outputFile)) {
                            Files.setPosixFilePermissions(outputFile, PosixFilePermissions.fromString("rwxr-xr-x"));
                        }
                        else {
                            Files.setPosixFilePermissions(outputFile, PosixFilePermissions.fromString("rw-r--r--"));
                        }
                    }
                }
                catch (Exception ex) {
                    logger.error("Error extracting sample", ex);
                    eventLog.logEvent(DownloadSampleActor.class, "Error extracting sample");

                    storageService.flagFileForDeletion(fileStorageId);
                    yield new ErrorCleanup(fileStorageId);
                }

                eventLog.logEvent(DownloadSampleActor.class, "Extraction complete");
                yield new SuccessCleanup(fileStorageId, tarFileName);
            }
            case SuccessCleanup(FileStorageId fileStorageId, String tarFile) -> {
                Files.deleteIfExists(Path.of(tarFile));
                storageService.setFileStorageState(fileStorageId, FileStorageState.UNSET);
                yield new End();
            }
            case ErrorCleanup(FileStorageId id) -> {
                storageService.flagFileForDeletion(id);
                yield new Error();
            }
            default -> new Error();
        };
    }

    private void transferBytes(InputStream inputStream, OutputStream outputStream, byte[] buffer, long size)
            throws IOException
    {
        long copiedSize = 0;

        while (copiedSize < size) {
            int read = inputStream.read(buffer);

            if (read < 0) // We've been promised a file of length 'size', so this shouldn't happen, but just in case...
                throw new IOException("Unexpected end of stream");

            outputStream.write(buffer, 0, read);
            copiedSize += read;
        }
    }


    private URL getDownloadURL(String setName) throws MalformedURLException {
        return URI.create("https://downloads.marginalia.nu/samples/" + setName + ".tar").toURL();
    }

    @Override
    public String describe() {
        return "Download a sample of crawl data from downloads.marginalia.nu";
    }

    @Inject
    public DownloadSampleActor(Gson gson,
                               FileStorageService storageService,
                               ServiceEventLog eventLog, ServiceHeartbeat heartbeat)
    {
        super(gson);
        this.storageService = storageService;
        this.eventLog = eventLog;
        this.heartbeat = heartbeat;
    }

}
