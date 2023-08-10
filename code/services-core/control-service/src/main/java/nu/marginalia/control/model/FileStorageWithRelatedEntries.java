package nu.marginalia.control.model;

import nu.marginalia.db.storage.model.FileStorage;
import nu.marginalia.db.storage.model.FileStorageType;

import java.util.List;

public record FileStorageWithRelatedEntries(FileStorageWithActions self,
                                            List<FileStorage> related,
                                            List<FileStorageFileModel> files
                                            ) {

}
