package nu.marginalia.index.searchset;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** A serializable bit map of domains corresponding to a method of ranking the domains
 *
 * @see SearchSetIdentifier
 *
 * */
public class RankingSearchSet implements SearchSet {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final IntOpenHashSet set;
    public final String name;
    public final Path source;

    public RankingSearchSet(String name, Path source, IntOpenHashSet set) {
        this.name = name;
        this.source = source;
        this.set = set;
    }

    public RankingSearchSet(String name, Path source) throws IOException {
        this.name = name;
        this.source = source;

        if (!Files.exists(source)) {
            set = new IntOpenHashSet();
        }
        else {
            set = load(source);
        }

        if (set.isEmpty()) {
            logger.warn("Search set {} is empty", name);
        }
    }

    private static IntOpenHashSet load(Path source) throws IOException {
        var set = new IntOpenHashSet();
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
    public boolean contains(int domainId) {

        // This is the main check
        if (set.contains(domainId) || set.isEmpty()) {
            return true;
        }

        // TODO
        return false;
    }

    @Override
    public IntList domainIds() {
        IntList ret = new IntArrayList(set);
        ret.sort(Integer::compareTo);
        return ret;
    }

    public void write() throws IOException {
        try (var ds = new DataOutputStream(Files.newOutputStream(source,
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING)))
        {
            for (var iter = set.intIterator(); iter.hasNext();) {
                ds.writeInt(iter.nextInt());
            }
        }
    }

    public String toString() {
        return name;
    }
}
