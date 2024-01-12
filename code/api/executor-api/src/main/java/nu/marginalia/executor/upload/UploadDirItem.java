package nu.marginalia.executor.upload;

import lombok.SneakyThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public record UploadDirItem (
        String name,
        String lastModifiedTime,
        boolean isDirectory,
        long size
) {

    @SneakyThrows
    public static UploadDirItem fromPath(Path path) {
        boolean isDir = Files.isDirectory(path);
        long size = isDir ? 0 : Files.size(path);
        var mtime = Files.getLastModifiedTime(path);


        return new UploadDirItem(path.toString(),
                LocalDateTime.ofInstant(mtime.toInstant(), ZoneId.systemDefault()).format(DateTimeFormatter.ISO_DATE_TIME), isDir, size);
    }

}
