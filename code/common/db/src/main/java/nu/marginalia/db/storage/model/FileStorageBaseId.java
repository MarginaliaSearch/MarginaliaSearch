package nu.marginalia.db.storage.model;

public record FileStorageBaseId(long id) {

    public String toString() {
        return Long.toString(id);
    }
}
