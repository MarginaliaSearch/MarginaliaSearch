package nu.marginalia.mqapi.loading;

import lombok.AllArgsConstructor;
import nu.marginalia.db.storage.model.FileStorageId;

@AllArgsConstructor
public class LoadRequest {
    public FileStorageId processedDataStorage;
}
