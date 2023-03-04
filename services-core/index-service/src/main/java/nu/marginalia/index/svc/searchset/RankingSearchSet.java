package nu.marginalia.index.svc.searchset;

import nu.marginalia.index.client.model.query.SearchSetIdentifier;
import nu.marginalia.index.searchset.SearchSet;
import org.roaringbitmap.RoaringBitmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** A serializable bit map of domains
 *
 * @see SearchSetIdentifier
 *
 * */
public class RankingSearchSet implements SearchSet {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final RoaringBitmap set;
    public final SearchSetIdentifier identifier;
    public final Path source;

    public RankingSearchSet(SearchSetIdentifier identifier, Path source, RoaringBitmap set) {
        this.identifier = identifier;
        this.source = source;
        this.set = set;
    }

    public RankingSearchSet(SearchSetIdentifier identifier, Path source) throws IOException {
        this.identifier = identifier;
        this.source = source;

        if (!Files.exists(source)) {
            set = new RoaringBitmap();
        }
        else {
            set = load(source);
        }

        if (set.isEmpty()) {
            logger.warn("Search set {} is empty", identifier);
        }
    }

    private static RoaringBitmap load(Path source) throws IOException {
        var set = new RoaringBitmap();
        try (var ds = new DataInputStream(Files.newInputStream(source, StandardOpenOption.READ))) {
            for (;;) {
                try {
                    set.add(ds.readInt());
                }
                catch (IOException ex) { break; }
            }
        }
        return set;
    }

    @Override
    public boolean contains(int urlId) {
        // Fallback on allow-all if no items are in set

        return set.contains(urlId) || set.isEmpty();
    }

    public void write() throws IOException {
        try (var ds = new DataOutputStream(Files.newOutputStream(source,
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING)))
        {
            for (var iter = set.getIntIterator(); iter.hasNext();) {
                ds.writeInt(iter.next());
            }
        }
    }

    public String toString() {
        return identifier.toString();
    }
}
