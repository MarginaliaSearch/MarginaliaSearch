package nu.marginalia.storage.model;

import java.nio.file.Path;

/**
 * Represents a file storage base directory
 *
 * @param id  the id of the storage base in the database
 * @param type  the type of the storage base
 * @param name  the name of the storage base
 * @param path  the path of the storage base
 */
public record FileStorageBase(FileStorageBaseId id,
                              FileStorageBaseType type,
                              int node,
                              String name,
                              String path
                              ) {
    public Path asPath() {
        return Path.of(path);
    }
    public boolean isValid() {
        return id.id() >= 0;
    }

}
