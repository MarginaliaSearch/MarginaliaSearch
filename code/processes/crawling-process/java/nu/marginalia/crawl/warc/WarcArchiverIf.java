package nu.marginalia.crawl.warc;

import java.io.IOException;
import java.nio.file.Path;

/** Interface for archiving warc files. */
public interface WarcArchiverIf extends AutoCloseable {
    /** Process the warc file.  After processing, the warc file is deleted.
     *  Processing may be a no-op, depending on the implementation.
     */
    void consumeWarc(Path warcFile, String domain) throws IOException;
    void close() throws IOException;
}
