package nu.marginalia.io;

import nu.marginalia.io.crawldata.format.SlopSerializableCrawlDataStream;
import nu.marginalia.model.crawldata.CrawledDocument;
import nu.marginalia.model.crawldata.CrawledDomain;
import nu.marginalia.model.crawldata.SerializableCrawlData;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** Closable iterator exceptional over serialized crawl data
 * The data may appear in any order, and the iterator must be closed.
 *
 * */
public interface SerializableCrawlDataStream extends AutoCloseable {
    Logger logger = LoggerFactory.getLogger(SerializableCrawlDataStream.class);

    SerializableCrawlData next() throws IOException;

    /** Return a size hint for the stream.  0 is returned if the hint is not available,
     * or if the file is seemed too small to bother */
    default int getSizeHint() { return 0; }

    boolean hasNext() throws IOException;

    @Nullable
    default Path path() { return null; }

    void close() throws IOException;

    /** An iterator-like access to domain data  This must be closed otherwise it will leak off-heap memory! */
    static SerializableCrawlDataStream openDataStream(Path fullPath) throws IOException
    {
        if (!Files.exists(fullPath)) {
            logger.error("Missing file: {}", fullPath);
            return SerializableCrawlDataStream.empty();
        }

        String fileName = fullPath.getFileName().toString();

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

    /** Get an idication of the size of the stream.  This is used to determine whether to
     * load the stream into memory or not.  0 is returned if the hint is not available,
     * or if the file is seemed too small to bother */
    static int getSizeHint(Path fullPath) {
        String fileName = fullPath.getFileName().toString();
        if (fileName.endsWith(".slop.zip")) {
            return SlopSerializableCrawlDataStream.sizeHint(fullPath);
        }
        else {
            return 0;
        }
    }

    default <T>  Iterator<T> map(Function<SerializableCrawlData, Optional<T>> mapper) {
        return new Iterator<>() {
            T next = null;

            public boolean hasNext() {
                if (next != null)
                    return true;
                try {
                    while (SerializableCrawlDataStream.this.hasNext()) {
                        var val = mapper.apply(SerializableCrawlDataStream.this.next());
                        if (val.isPresent()) {
                            next = val.get();
                            return true;
                        }
                    }
                }
                catch (IOException ex) {
                    logger.error("Error during stream", ex);
                }

                return false;
            }

            public T next() {
                if (next == null && !hasNext())
                    throw new IllegalStateException("No more data to read");

                T ret = next;
                next = null;
                return ret;
            }
        };

    }

    /** For tests */
    default List<SerializableCrawlData> asList() throws IOException {
        List<SerializableCrawlData> data = new ArrayList<>();
        while (hasNext()) {
            data.add(next());
        }
        return data;
    }

    /** For tests */
    default List<CrawledDocument> docsAsList() throws IOException {
        List<CrawledDocument> data = new ArrayList<>();
        while (hasNext()) {
            if (next() instanceof CrawledDocument doc) {
                data.add(doc);
            }
        }
        return data;
    }

    /** For tests */
    default List<CrawledDomain> domainsAsList() throws IOException {
        List<CrawledDomain> data = new ArrayList<>();
        while (hasNext()) {
            if (next() instanceof CrawledDomain domain) {
                data.add(domain);
            }
        }
        return data;
    }

    // Dummy iterator over nothing
    static SerializableCrawlDataStream empty() {
        return new SerializableCrawlDataStream() {
            @Override
            public SerializableCrawlData next() throws IOException { throw new IllegalStateException("No more data"); }
            @Override
            public boolean hasNext() throws IOException { return false;}
            public void close() {}
        };
    }

    // for testing
    static SerializableCrawlDataStream fromIterator(Iterator<SerializableCrawlData> iterator) {
        return new SerializableCrawlDataStream() {
            @Override
            public SerializableCrawlData next() { return iterator.next(); }
            @Override
            public boolean hasNext() { return iterator.hasNext(); }
            public void close() {}
        };
    }

}
