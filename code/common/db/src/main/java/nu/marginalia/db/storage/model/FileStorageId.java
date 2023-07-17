package nu.marginalia.db.storage.model;

public record FileStorageId(long id) {
    public static FileStorageId of(int storageId) {
        return new FileStorageId(storageId);
    }

    public String toString() {
        return Long.toString(id);
    }
}
