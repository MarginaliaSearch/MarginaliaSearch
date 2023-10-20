package nu.marginalia.storage.model;

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
public record FileStorage (
        FileStorageId id,
        FileStorageBase base,
        FileStorageType type,
        LocalDateTime createDateTime,
        String path,
        FileStorageState state,
        String description)
{

    /** It is sometimes desirable to be able to create an override that isn't
     * backed by the database.  This constructor permits this.
     */
    public static FileStorage createOverrideStorage(FileStorageType type, FileStorageBaseType baseType, String override) {
        var mockBase = new FileStorageBase(
                new FileStorageBaseId(-1),
                baseType,
                "OVERRIDE:" + type.name(),
                "INVALIDINVALIDINVALID"
        );

        return new FileStorage(
                new FileStorageId(-1),
                mockBase,
                type,
                LocalDateTime.now(),
                override,
                FileStorageState.EPHEMERAL,
                "OVERRIDE:" + type.name()
        );
    }

    public Path asPath() {
        return Path.of(path);
    }

    public boolean isActive() {
        return FileStorageState.ACTIVE.equals(state);
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
