package nu.marginalia.executor.model.crawl;

import nu.marginalia.storage.model.FileStorageId;

import java.util.List;

public record RecrawlParameters(
        FileStorageId crawlDataId,
        List<FileStorageId> crawlSpecIds
) {
}
