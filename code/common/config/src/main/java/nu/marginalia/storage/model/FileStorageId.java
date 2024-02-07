package nu.marginalia.storage.model;

public record FileStorageId(long id) {
    public static FileStorageId parse(String str) {
        return new FileStorageId(Long.parseLong(str));
    }
    public static FileStorageId of(long storageId) {
        return new FileStorageId(storageId);
    }

    public String toString() {
        return Long.toString(id);
    }
}
