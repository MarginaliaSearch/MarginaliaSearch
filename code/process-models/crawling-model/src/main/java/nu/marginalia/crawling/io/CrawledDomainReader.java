package nu.marginalia.crawling.io;

import com.google.gson.Gson;
import nu.marginalia.crawling.io.format.LegacyFileReadingSerializableCrawlDataStream;
import nu.marginalia.crawling.io.format.WarcReadingSerializableCrawlDataStream;
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
            return new LegacyFileReadingSerializableCrawlDataStream(gson, fullPath.toFile());
        }
        else if (fileName.endsWith(".warc") || fileName.endsWith(".warc.gz")) {
            return new WarcReadingSerializableCrawlDataStream(fullPath);
        }
        else {
            throw new IllegalArgumentException("Unknown file type: " + fullPath);
        }
    }

    /** An iterator-like access to domain data. This must be closed otherwise it will leak off-heap memory! */
    public static SerializableCrawlDataStream createDataStream(Path basePath, String domain, String id) throws IOException {
        Path warcPath = CrawlerOutputFile.getWarcPath(basePath, id, domain, CrawlerOutputFile.WarcFileVersion.FINAL);

        if (Files.exists(warcPath)) {
            return createDataStream(warcPath);
        }
        else {
            return createDataStream(CrawlerOutputFile.getLegacyOutputFile(basePath, id, domain));
        }
    }

}
