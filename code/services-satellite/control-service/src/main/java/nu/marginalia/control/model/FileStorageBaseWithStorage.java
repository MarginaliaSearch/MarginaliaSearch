package nu.marginalia.control.model;

import nu.marginalia.db.storage.model.FileStorage;
import nu.marginalia.db.storage.model.FileStorageBase;

import java.util.List;

public record FileStorageBaseWithStorage(FileStorageBase base, List<FileStorage> storage) {
}
