package nu.marginalia.crawling.io;

import nu.marginalia.crawling.io.format.ParquetSerializableCrawlDataStream;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class CrawledDomainReader {

    /** An iterator-like access to domain data  This must be closed otherwise it will leak off-heap memory! */
    public static SerializableCrawlDataStream createDataStream(Path fullPath) throws IOException
    {
        String fileName = fullPath.getFileName().toString();
        if (fileName.endsWith(".parquet")) {
            return new ParquetSerializableCrawlDataStream(fullPath);
        }
        else {
            throw new IllegalArgumentException("Unknown file type: " + fullPath);
        }
    }

    /** An iterator-like access to domain data. This must be closed otherwise it will leak off-heap memory! */
    public static SerializableCrawlDataStream createDataStream(Path basePath, String domain, String id) throws IOException {
        Path parquetPath = CrawlerOutputFile.getParquetPath(basePath, id, domain);

        if (Files.exists(parquetPath)) {
            return createDataStream(parquetPath);
        }
        else {
            throw new FileNotFoundException("No such file: " + parquetPath);
        }
    }

}
