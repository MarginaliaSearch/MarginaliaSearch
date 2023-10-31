package nu.marginalia.executor.model.load;

import nu.marginalia.storage.model.FileStorageId;

import java.util.List;

public record LoadParameters(
        List<FileStorageId> ids
) {
}
