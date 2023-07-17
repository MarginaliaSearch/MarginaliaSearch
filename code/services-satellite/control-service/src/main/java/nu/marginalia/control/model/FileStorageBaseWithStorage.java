package nu.marginalia.control.model;

import nu.marginalia.db.storage.model.FileStorageBase;

import java.util.List;

public record FileStorageBaseWithStorage(FileStorageBase base,
                                         List<FileStorageWithActions> storage)
{
}
