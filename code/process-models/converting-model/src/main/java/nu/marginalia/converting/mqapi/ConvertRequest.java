package nu.marginalia.converting.mqapi;

import lombok.AllArgsConstructor;
import nu.marginalia.db.storage.model.FileStorageId;

@AllArgsConstructor
public class ConvertRequest {
    public final FileStorageId crawlStorage;
    public final FileStorageId processedDataStorage;
}
