package nu.marginalia.db.storage.model;

import java.nio.file.Path;

/**
 * Represents a file storage area
 *
 * @param id  the id of the storage in the database
 * @param base the base of the storage
 * @param type the type of data expected
 * @param path the full path of the storage on disk
 * @param description a description of the storage
 */
public record FileStorage(
        FileStorageId id,
        FileStorageBase base,
        FileStorageType type,
        String path,
        String description)
{
    public Path asPath() {
        return Path.of(path);
    }
}
