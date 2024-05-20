package nu.marginalia.control.node.model;

import nu.marginalia.storage.model.FileStorage;
import nu.marginalia.storage.model.FileStorageId;
import nu.marginalia.storage.model.FileStorageType;

import java.util.List;

public record FileStorageWithRelatedEntries(FileStorageWithActions self,
                                            List<FileStorage> related,
                                            List<FileStorageFileModel> files
                                            ) {
    public FileStorageType type() {
        return self().storage().type();
    }

    public FileStorageId getId() {
        return self.storage().id();
    }
}
