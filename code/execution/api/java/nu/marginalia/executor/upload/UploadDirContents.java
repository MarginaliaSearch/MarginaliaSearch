package nu.marginalia.executor.upload;

import java.util.List;

public record UploadDirContents(String path, List<UploadDirItem> items) {
}
