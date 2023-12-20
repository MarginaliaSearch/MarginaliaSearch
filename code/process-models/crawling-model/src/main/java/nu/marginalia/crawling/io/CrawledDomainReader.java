package nu.marginalia.crawling.io;

import com.google.gson.Gson;
import nu.marginalia.crawling.io.format.LegacySerializableCrawlDataStream;
import nu.marginalia.crawling.io.format.ParquetSerializableCrawlDataStream;
import nu.marginalia.model.gson.GsonFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class CrawledDomainReader {
    private static final Gson gson = GsonFactory.get();

    public CrawledDomainReader() {
    }

    /** An iterator-like access to domain data  This must be closed otherwise it will leak off-heap memory! */
    public static SerializableCrawlDataStream createDataStream(Path fullPath) throws IOException {
        String fileName = fullPath.getFileName().toString();
        if (fileName.endsWith(".zstd")) {
            return new LegacySerializableCrawlDataStream(gson, fullPath.toFile());
        }
        else if (fileName.endsWith(".parquet")) {
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
            return createDataStream(CrawlerOutputFile.getLegacyOutputFile(basePath, id, domain));
        }
    }

}
