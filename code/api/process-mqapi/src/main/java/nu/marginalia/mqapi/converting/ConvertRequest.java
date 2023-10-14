package nu.marginalia.mqapi.converting;

import lombok.AllArgsConstructor;
import nu.marginalia.storage.model.FileStorageId;

@AllArgsConstructor
public class ConvertRequest {
    public final ConvertAction action;
    public final String inputSource;
    public final FileStorageId crawlStorage;
    public final FileStorageId processedDataStorage;
}
