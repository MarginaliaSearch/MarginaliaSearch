package nu.marginalia.control.model;

import nu.marginalia.db.storage.model.FileStorage;

import java.util.List;

public record FileStorageFileModel(String filename,
                                   String type,
                                   String size
                                            ) {

    public boolean isDownloadable() {
        return type.equals("file");
    }
}
