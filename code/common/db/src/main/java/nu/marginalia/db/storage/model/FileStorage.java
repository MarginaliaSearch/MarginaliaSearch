package nu.marginalia.db.storage.model;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Objects;

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
        LocalDateTime createDateTime,
        String path,
        String description)
{
    public Path asPath() {
        return Path.of(path);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FileStorage that = (FileStorage) o;

        // Exclude timestamp as it may different due to how the objects
        // are constructed

        if (!Objects.equals(id, that.id)) return false;
        if (!Objects.equals(base, that.base)) return false;
        if (type != that.type) return false;
        if (!Objects.equals(path, that.path)) return false;
        return Objects.equals(description, that.description);
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (base != null ? base.hashCode() : 0);
        result = 31 * result + (type != null ? type.hashCode() : 0);
        result = 31 * result + (path != null ? path.hashCode() : 0);
        result = 31 * result + (description != null ? description.hashCode() : 0);
        return result;
    }
}
