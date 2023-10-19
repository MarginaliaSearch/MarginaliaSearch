package nu.marginalia.executor.model.transfer;

import nu.marginalia.storage.model.FileStorageId;

public record TransferItem(String domainName,
                           int domainId,
                           FileStorageId fileStorageId,
                           String path) {
}
