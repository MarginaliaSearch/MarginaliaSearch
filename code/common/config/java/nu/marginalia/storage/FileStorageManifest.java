package nu.marginalia.storage;

import com.google.gson.Gson;
import nu.marginalia.model.gson.GsonFactory;
import nu.marginalia.storage.model.FileStorage;
import nu.marginalia.storage.model.FileStorageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

record FileStorageManifest(FileStorageType type, String description) {
    private static final Gson gson = GsonFactory.get();
    private static final String fileName = "marginalia-manifest.json";
    private static final Logger logger = LoggerFactory.getLogger(FileStorageManifest.class);

    public static Optional<FileStorageManifest> find(Path directory) {
        Path expectedFileName = directory.resolve(fileName);

        if (!Files.isRegularFile(expectedFileName) ||
            !Files.isReadable(expectedFileName)) {
            return Optional.empty();
        }

        try (var reader = Files.newBufferedReader(expectedFileName)) {
            return Optional.of(gson.fromJson(reader, FileStorageManifest.class));
        }
        catch (Exception e) {
            logger.warn("Failed to read manifest " + expectedFileName, e);
            return Optional.empty();
        }
    }

    public void write(FileStorage dir) {
        Path expectedFileName = dir.asPath().resolve(fileName);

        try (var writer = Files.newBufferedWriter(expectedFileName,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING))
        {
            gson.toJson(this, writer);
        }
        catch (Exception e) {
            logger.warn("Failed to write manifest " + expectedFileName, e);
        }
    }

}
