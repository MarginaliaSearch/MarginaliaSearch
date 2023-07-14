package nu.marginalia.db.storage.model;

import java.nio.file.Path;

/**
 * Represents a file storage base directory
 *
 * @param id  the id of the storage base in the database
 * @param type  the type of the storage base
 * @param name  the name of the storage base
 * @param path  the path of the storage base
 * @param mustClean  if true, the storage is small and *must* be cleaned after use
 * @param permitTemp if true, the storage may be used for temporary files
 */
public record FileStorageBase(FileStorageBaseId id,
                              FileStorageBaseType type,
                              String name,
                              String path,
                              boolean mustClean,
                              boolean permitTemp
                              ) {
    public Path asPath() {
        return Path.of(path);
    }
}
