package nu.marginalia.storage.model;

public enum FileStorageBaseType {
    CURRENT,
    WORK,
    STORAGE,
    BACKUP;

    public String overrideName() {
        return "FS_BASE_OVERRIDE:"+name();
    }
}
