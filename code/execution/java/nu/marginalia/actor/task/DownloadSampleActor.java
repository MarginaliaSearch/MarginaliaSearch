package nu.marginalia.actor.task;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.actor.prototype.RecordActorPrototype;
import nu.marginalia.actor.state.ActorStep;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.*;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public record Run(String setName) implements ActorStep {}
    @Override
    public ActorStep transition(ActorStep self) throws Exception {
        return switch(self) {
            case Run(String setName) -> {
                final FileStorage newStorage = storageService.allocateStorage(
                        FileStorageType.CRAWL_DATA,
                        "sample-crawl-data",
                        "Sample " + setName);

                storageService.setFileStorageState(newStorage.id(), FileStorageState.NEW);

                URL downloadURI = getDownloadURL(setName);

                try {
                    downloadArchive(downloadURI, newStorage.asPath());
                }
                catch (IOException ex) {
                    logger.error("Error downloading sample", ex);
                    storageService.flagFileForDeletion(newStorage.id());
                    yield new Error();
                }
                finally {
                    storageService.setFileStorageState(newStorage.id(), FileStorageState.UNSET);
                }

                yield new End();
            }
            default -> new Error();
        };
    }

    private void downloadArchive(URL downloadURI, Path outputPath) throws IOException, InterruptedException {
        // See the documentation for commons compress:
        // https://commons.apache.org/proper/commons-compress/examples.html

        try (var tar = new TarArchiveInputStream(downloadURI.openStream())) {
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

                if (Files.isDirectory(outputPath)) {
                    Files.setPosixFilePermissions(outputPath, PosixFilePermissions.fromString("rwxr-xr-x"));
                }
                else {
                    Files.setPosixFilePermissions(outputPath, PosixFilePermissions.fromString("rw-r--r--"));
                }
            }
        }
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
        return URI.create(STR."https://downloads.marginalia.nu/samples/\{setName}.tar").toURL();
    }

    @Override
    public String describe() {
        return "Download a sample of crawl data from downloads.marginalia.nu";
    }

    @Inject
    public DownloadSampleActor(Gson gson,
                               FileStorageService storageService)
    {
        super(gson);
        this.storageService = storageService;
    }

}
