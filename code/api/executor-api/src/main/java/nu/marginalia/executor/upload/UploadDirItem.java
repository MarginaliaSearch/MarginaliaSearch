package nu.marginalia.executor.upload;

public record UploadDirItem (
        String name,
        String lastModifiedTime,
        boolean isDirectory,
        long size
) {
}
