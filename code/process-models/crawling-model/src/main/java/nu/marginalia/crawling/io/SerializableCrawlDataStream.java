package nu.marginalia.crawling.io;

import nu.marginalia.crawling.model.SerializableCrawlData;

import java.io.IOException;
import java.util.Iterator;

/** Closable iterator over serialized crawl data
 * The data may appear in any order, and the iterator must be closed.
 * */
public interface SerializableCrawlDataStream extends AutoCloseable {
    static SerializableCrawlDataStream empty() {
        return new SerializableCrawlDataStream() {
            @Override
            public SerializableCrawlData next() throws IOException {
                throw new IllegalStateException("No more data");
            }

            @Override
            public boolean hasNext() throws IOException {
                return false;
            }

            public void close() {}
        };
    }

    // for testing
    static SerializableCrawlDataStream fromIterator(Iterator<SerializableCrawlData> iterator) {
        return new SerializableCrawlDataStream() {
            @Override
            public SerializableCrawlData next() throws IOException {
                return iterator.next();
            }

            @Override
            public boolean hasNext() throws IOException {
                return iterator.hasNext();
            }

            public void close() {}
        };

    }

    SerializableCrawlData next() throws IOException;

    boolean hasNext() throws IOException;
}
