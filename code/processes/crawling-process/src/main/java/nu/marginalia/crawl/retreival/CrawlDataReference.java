package nu.marginalia.crawl.retreival;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import nu.marginalia.crawling.io.SerializableCrawlDataStream;
import nu.marginalia.crawling.model.CrawledDocument;
import nu.marginalia.lsh.EasyLSH;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** A reference to a domain that has been crawled before. */
public class CrawlDataReference implements AutoCloseable {

    private final SerializableCrawlDataStream data;
    private static final Logger logger = LoggerFactory.getLogger(CrawlDataReference.class);

    public CrawlDataReference(SerializableCrawlDataStream data) {
        this.data = data;
    }

    public CrawlDataReference() {
        this(SerializableCrawlDataStream.empty());
    }

    /** Delete the associated data from disk, if it exists */
    public void delete() throws IOException {
        Path filePath = data.path();

        if (filePath != null) {
            Files.deleteIfExists(filePath);
        }
    }

    @Nullable
    public CrawledDocument nextDocument() {
        try {
            while (data.hasNext()) {
                if (data.next() instanceof CrawledDocument doc) {
                    return doc;
                }
            }
        }
        catch (IOException ex) {
            logger.error("Failed to read next document", ex);
        }

        return null;
    }

    public boolean isContentBodySame(String one, String other) {

        final long contentHashOne = contentHash(one);
        final long contentHashOther = contentHash(other);

        return EasyLSH.hammingDistance(contentHashOne, contentHashOther) < 4;
    }

    private long contentHash(String content) {
        EasyLSH hash = new EasyLSH();
        int next = 0;

        boolean isInTag = false;

        // In a naive best-effort fashion, extract the text
        // content of the document and feed it into the LSH
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '<') {
                isInTag = true;
            } else if (c == '>') {
                isInTag = false;
            } else if (!isInTag) {
                next = (next << 8) | (c & 0xff);
                hash.addHashUnordered(hashInt(next));
            }
        }

        return hash.get();
    }

    private final HashFunction hashFunction = Hashing.murmur3_128();
    private int hashInt(int v) {
        return hashFunction.hashInt(v).asInt();
    }

    @Override
    public void close() throws Exception {
        data.close();
    }
}
