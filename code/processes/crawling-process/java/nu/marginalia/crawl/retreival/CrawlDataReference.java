package nu.marginalia.crawl.retreival;

import nu.marginalia.ContentTypes;
import nu.marginalia.io.SerializableCrawlDataStream;
import nu.marginalia.lsh.EasyLSH;
import nu.marginalia.model.crawldata.CrawledDocument;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;

/** A reference to a domain that has been crawled before. */
public class CrawlDataReference implements AutoCloseable, Iterable<CrawledDocument> {

    private boolean closed = false;

    @Nullable
    private final Path path;

    @Nullable
    private SerializableCrawlDataStream data = null;

    private static final Logger logger = LoggerFactory.getLogger(CrawlDataReference.class);

    public CrawlDataReference(@Nullable Path path) {
        this.path = path;
    }

    public CrawlDataReference() {
        this(null);
    }

    /** Delete the associated data from disk, if it exists */
    public void delete() throws IOException {
        if (path != null) {
            Files.deleteIfExists(path);
        }
    }

    public @NotNull Iterator<CrawledDocument> iterator() {

        requireStream();
        // Guaranteed by requireStream, but helps java
        Objects.requireNonNull(data);

        return data.map(next -> {
            if (next instanceof CrawledDocument doc && ContentTypes.isAccepted(doc.contentType)) {
                return Optional.of(doc);
            }
            else {
                return Optional.empty();
            }
        });
    }

    /** After calling this method, data is guaranteed to be non-null */
    private void requireStream() {
        if (closed) {
            throw new IllegalStateException("Use after close()");
        }

        if (data == null) {
            try {
                if (path != null) {
                    data = SerializableCrawlDataStream.openDataStream(path);
                    return;
                }
            }
            catch (Exception ex) {
                logger.error("Failed to open stream", ex);
            }

            data = SerializableCrawlDataStream.empty();
        }
    }

    public static boolean isContentBodySame(byte[] one, byte[] other) {

        final long contentHashOne = contentHash(one);
        final long contentHashOther = contentHash(other);

        return EasyLSH.hammingDistance(contentHashOne, contentHashOther) < 4;
    }

    private static long contentHash(byte[] content) {
        EasyLSH hash = new EasyLSH();
        int next = 0;

        boolean isInTag = false;

        // In a naive best-effort fashion, extract the text
        // content of the document and feed it into the LSH
        for (byte b : content) {
            char c = (char) b;
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

    // https://stackoverflow.com/a/12996028
    private static int hashInt(int x) {
        x = (((x >>> 16) ^ x) * 0x45d9f3b);
        x = (((x >>> 16) ^ x) * 0x45d9f3b);
        x = (x >>> 16) ^ x;
        return x;
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            if (data != null) {
                data.close();
            }
            closed = true;
        }
    }
}
