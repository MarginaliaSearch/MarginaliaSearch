package nu.marginalia.mqapi.tasks;

import nu.marginalia.storage.model.FileStorageId;

public class ExportTaskRequest {
    public enum Task {
        ATAGS,
        FEEDS,
        TERM_FREQ,
        SAMPLE_DATA,
        ADJACENCIES,
    }

    public Task task;
    public FileStorageId crawlId;
    public FileStorageId destId;
    public int size;
    public String name;
    public String ctFilter;

    public ExportTaskRequest(Task task) {
        this.task = task;
    }

    public static ExportTaskRequest atags(FileStorageId crawlId, FileStorageId destId) {
        ExportTaskRequest request = new ExportTaskRequest(Task.ATAGS);
        request.crawlId = crawlId;
        request.destId = destId;
        return request;
    }

    public static ExportTaskRequest feeds(FileStorageId crawlId, FileStorageId destId) {
        ExportTaskRequest request = new ExportTaskRequest(Task.FEEDS);
        request.crawlId = crawlId;
        request.destId = destId;
        return request;
    }

    public static ExportTaskRequest termFreq(FileStorageId crawlId, FileStorageId destId) {
        ExportTaskRequest request = new ExportTaskRequest(Task.TERM_FREQ);
        request.crawlId = crawlId;
        request.destId = destId;
        return request;
    }

    public static ExportTaskRequest sampleData(FileStorageId crawlId, FileStorageId destId, String ctFilter, int size, String name) {
        ExportTaskRequest request = new ExportTaskRequest(Task.SAMPLE_DATA);
        request.crawlId = crawlId;
        request.destId = destId;
        request.size = size;
        request.name = name;
        request.ctFilter = ctFilter;
        return request;
    }

    public static ExportTaskRequest adjacencies() {
        return new ExportTaskRequest(Task.ADJACENCIES);
    }
}
