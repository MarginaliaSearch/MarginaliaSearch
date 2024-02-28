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

import static java.util.Objects.*;

/** This class is used to read the old format of crawl data, which was zstd-compressed JSON
 * with type delimiters between records.  It does its best to preserve the semantics of the
 * new format.  This is slow.
 */
public class CompatibleLegacySerializableCrawlDataStream implements AutoCloseable, SerializableCrawlDataStream {
    private final Gson gson;
    private final BufferedReader bufferedReader;

    private CrawledDomain domain;
    private SerializableCrawlData next;

    private final Path path;
    private int sizeHint;

    public CompatibleLegacySerializableCrawlDataStream(Gson gson, File file) throws IOException {
        this.gson = gson;
        path = file.toPath();
        domain = findDomain(file);

        bufferedReader = new BufferedReader(new InputStreamReader(new ZstdInputStream(new FileInputStream(file), RecyclingBufferPool.INSTANCE)));
    }

    @Override
    public int sizeHint() {
        return sizeHint;
    }

    /** Scan through the file and find the domain record */
    private CrawledDomain findDomain(File file) throws IOException {
        try (var br = new BufferedReader(new InputStreamReader(new ZstdInputStream(new FileInputStream(file), RecyclingBufferPool.INSTANCE)))) {
            for (;;sizeHint++) {
                String identifierLine =
                        requireNonNull(br.readLine(), "No identifier line found");
                String dataLine =
                        requireNonNull(br.readLine(), "No data line found");

                if (identifierLine.equals(CrawledDomain.SERIAL_IDENTIFIER)) {
                    return gson.fromJson(dataLine, CrawledDomain.class);
                }
            }
        }
    }

    @Override
    public Path path() {
        return path;
    }

    @Override
    public SerializableCrawlData next() throws IOException {
        if (hasNext()) {
            if (domain != null) {
                var ret = domain;
                domain = null;
                return ret;
            }
            else {
                var ret = next;
                next = null;
                return ret;
            }
        }
        throw new IllegalStateException("No more data");
    }

    @Override
    public boolean hasNext() throws IOException {
        if (domain != null || next != null) {
            return true;
        }

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
            next = null;
            return false; // last record is expected to be the domain, so we're done
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
