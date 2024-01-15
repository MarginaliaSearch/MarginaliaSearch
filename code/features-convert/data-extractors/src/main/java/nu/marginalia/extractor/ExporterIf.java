package nu.marginalia.extractor;

import nu.marginalia.storage.model.FileStorageId;

public interface ExporterIf {
    void export(FileStorageId crawlId, FileStorageId destId) throws Exception;
}
