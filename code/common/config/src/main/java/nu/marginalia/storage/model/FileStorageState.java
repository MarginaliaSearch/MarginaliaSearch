package nu.marginalia.storage.model;

public enum FileStorageState {
    UNSET,
    NEW,
    ACTIVE,
    DELETE,
    EPHEMERAL;

    public static FileStorageState parse(String value) {
        if ("".equals(value)) {
            return UNSET;
        }
        return valueOf(value);
    }
}
