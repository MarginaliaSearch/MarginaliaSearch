package nu.marginalia.mqapi.loading;

import nu.marginalia.storage.model.FileStorageId;

import java.util.List;

public class LoadRequest {
    public List<FileStorageId> inputProcessDataStorageIds;

    public LoadRequest(List<FileStorageId> inputProcessDataStorageIds) {
        this.inputProcessDataStorageIds = inputProcessDataStorageIds;
    }
}
