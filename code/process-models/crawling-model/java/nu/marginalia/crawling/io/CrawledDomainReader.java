package nu.marginalia.crawling.io;

import com.google.gson.Gson;
import nu.marginalia.crawling.io.format.CompatibleLegacySerializableCrawlDataStream;
import nu.marginalia.crawling.io.format.FastLegacySerializableCrawlDataStream;
import nu.marginalia.crawling.io.format.ParquetSerializableCrawlDataStream;
import nu.marginalia.model.gson.GsonFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class CrawledDomainReader {
    private static final Gson gson = GsonFactory.get();

    public CrawledDomainReader() {
    }

    public enum CompatibilityLevel {
        /** Data order emulates the ordering of the new format.  This is slower */
        COMPATIBLE,
        /** Data order is not compatible with the new format, but the data itself is */
        FAST,
        /** Alias for FAST */
        ANY
    }
    /** An iterator-like access to domain data  This must be closed otherwise it will leak off-heap memory! */
    public static SerializableCrawlDataStream createDataStream(CompatibilityLevel compatibilityLevel,
                                                               Path fullPath) throws IOException
    {
        String fileName = fullPath.getFileName().toString();
        if (fileName.endsWith(".zstd")) {
            if (compatibilityLevel == CompatibilityLevel.COMPATIBLE)
                return new CompatibleLegacySerializableCrawlDataStream(gson, fullPath.toFile());
            else // if (compatibilityLevel == CompatibilityLevel.FAST or ANY)
                return new FastLegacySerializableCrawlDataStream(gson, fullPath.toFile());
        }
        else if (fileName.endsWith(".parquet")) {
            return new ParquetSerializableCrawlDataStream(fullPath);
        }
        else {
            throw new IllegalArgumentException("Unknown file type: " + fullPath);
        }
    }

    /** An iterator-like access to domain data. This must be closed otherwise it will leak off-heap memory! */
    public static SerializableCrawlDataStream createDataStream(CompatibilityLevel level, Path basePath, String domain, String id) throws IOException {
        Path parquetPath = CrawlerOutputFile.getParquetPath(basePath, id, domain);

        if (Files.exists(parquetPath)) {
            return createDataStream(level, parquetPath);
        }
        else {
            return createDataStream(level, CrawlerOutputFile.getLegacyOutputFile(basePath, id, domain));
        }
    }

}
