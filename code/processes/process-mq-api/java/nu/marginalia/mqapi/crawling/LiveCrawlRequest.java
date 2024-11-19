package nu.marginalia.mqapi.crawling;

import nu.marginalia.storage.model.FileStorageId;

public class LiveCrawlRequest {
    public FileStorageId liveDataFileStorageId;

    public LiveCrawlRequest(FileStorageId liveDataFileStorageId) {
        this.liveDataFileStorageId = liveDataFileStorageId;
    }
}
