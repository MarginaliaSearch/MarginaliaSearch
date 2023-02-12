package nu.marginalia.wmsa.edge.index.svc.searchset;

import org.roaringbitmap.RoaringBitmap;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** A serializable bit map of domains */
public class RankingSearchSet implements SearchSet {

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
        return set.contains(urlId);
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
