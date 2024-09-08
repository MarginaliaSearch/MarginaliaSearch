package nu.marginalia.io.crawldata;

import nu.marginalia.io.crawldata.format.ParquetSerializableCrawlDataStream;
import nu.marginalia.crawling.io.format.ParquetSerializableCrawlDataStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class CrawledDomainReader {
    private static final Logger logger = LoggerFactory.getLogger(CrawledDomainReader.class);

    /** An iterator-like access to domain data  This must be closed otherwise it will leak off-heap memory! */
    public static SerializableCrawlDataStream createDataStream(Path fullPath) throws IOException
    {

        String fileName = fullPath.getFileName().toString();
        if (fileName.endsWith(".parquet")) {
            try {
                return new ParquetSerializableCrawlDataStream(fullPath);
            } catch (Exception ex) {
                logger.error("Error reading domain data from " + fullPath, ex);
                return SerializableCrawlDataStream.empty();
            }
        } else {
            logger.error("Unknown file type: {}", fullPath);
            return SerializableCrawlDataStream.empty();
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
