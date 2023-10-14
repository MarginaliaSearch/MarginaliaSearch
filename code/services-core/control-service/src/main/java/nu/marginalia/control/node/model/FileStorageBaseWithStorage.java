package nu.marginalia.control.node.model;

import nu.marginalia.storage.model.FileStorageBase;

import java.util.List;

public record FileStorageBaseWithStorage(FileStorageBase base,
                                         List<FileStorageWithActions> storage)
{
}
