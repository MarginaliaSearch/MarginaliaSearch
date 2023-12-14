package nu.marginalia.crawling.io.format;

import com.github.luben.zstd.RecyclingBufferPool;
import com.github.luben.zstd.ZstdInputStream;
import com.google.gson.Gson;
import nu.marginalia.crawling.io.SerializableCrawlDataStream;
import nu.marginalia.crawling.model.CrawledDocument;
import nu.marginalia.crawling.model.CrawledDomain;
import nu.marginalia.crawling.model.SerializableCrawlData;

import java.io.*;
import java.nio.file.Path;

/** This class is used to read the old format of crawl data, which was zstd-compressed JSON
 * with type delimiters between records.
 */
public class LegacySerializableCrawlDataStream implements AutoCloseable, SerializableCrawlDataStream {
    private final Gson gson;
    private final BufferedReader bufferedReader;
    private SerializableCrawlData next = null;

    private final Path path;
    public LegacySerializableCrawlDataStream(Gson gson, File file) throws IOException {
        this.gson = gson;
        bufferedReader = new BufferedReader(new InputStreamReader(new ZstdInputStream(new FileInputStream(file), RecyclingBufferPool.INSTANCE)));
        path = file.toPath();
    }

    @Override
    public Path path() {
        return path;
    }
    @Override
    public SerializableCrawlData next() throws IOException {
        if (hasNext()) {
            var ret = next;
            next = null;
            return ret;
        }
        throw new IllegalStateException("No more data");
    }

    @Override
    public boolean hasNext() throws IOException {
        if (next != null)
            return true;

        String identifier = bufferedReader.readLine();
        if (identifier == null) {
            bufferedReader.close();
            return false;
        }
        String data = bufferedReader.readLine();
        if (data == null) {
            bufferedReader.close();
            return false;
        }

        if (identifier.equals(CrawledDomain.SERIAL_IDENTIFIER)) {
            next = gson.fromJson(data, CrawledDomain.class);
        } else if (identifier.equals(CrawledDocument.SERIAL_IDENTIFIER)) {
            next = gson.fromJson(data, CrawledDocument.class);
        } else {
            throw new IllegalStateException("Unknown identifier: " + identifier);
        }
        return true;
    }

    @Override
    public void close() throws Exception {
        bufferedReader.close();
    }
}
