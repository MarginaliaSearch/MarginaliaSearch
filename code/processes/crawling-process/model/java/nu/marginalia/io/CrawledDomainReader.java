package nu.marginalia.io;

import nu.marginalia.io.crawldata.format.ParquetSerializableCrawlDataStream;
import nu.marginalia.io.crawldata.format.SlopSerializableCrawlDataStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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
        }

        if (fileName.endsWith(".slop.zip")) {
            try {
                return new SlopSerializableCrawlDataStream(fullPath);
            } catch (Exception ex) {
                logger.error("Error reading domain data from " + fullPath, ex);
                return SerializableCrawlDataStream.empty();
            }
        }

        logger.error("Unknown file type: {}", fullPath);
        return SerializableCrawlDataStream.empty();
    }

}
