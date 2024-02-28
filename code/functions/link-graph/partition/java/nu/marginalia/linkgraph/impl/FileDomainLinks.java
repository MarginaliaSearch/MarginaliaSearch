package nu.marginalia.linkgraph.impl;

import com.google.inject.name.Named;
import gnu.trove.list.array.TIntArrayList;
import nu.marginalia.linkgraph.DomainLinks;
import nu.marginalia.linkgraph.io.DomainLinksLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

/** Canonical DomainLinkDb implementation.  The database is loaded into memory from
 * a file.  The database is then kept in memory, reloading it upon switchInput().
  */
public class FileDomainLinks implements DomainLinks {
    private static final Logger logger = LoggerFactory.getLogger(FileDomainLinks.class);
    private final Path filename;
    private volatile long[] sourceToDest = new long[0];
    private volatile long[] destToSource = new long[0];

    public FileDomainLinks(@Named("domain-linkdb-file") Path filename) throws IOException {
        this.filename = filename;

        if (Files.exists(filename)) {
            loadInput(filename);
        }
    }

    @Override
    public void switchInput(Path newFilename) throws IOException {
        Files.move(newFilename, filename, StandardCopyOption.REPLACE_EXISTING);
        loadInput(filename);
    }

    public void loadInput(Path filename) throws IOException {
        try (var loader = new DomainLinksLoader(filename)) {
            int size = loader.size();

            var newSourceToDest = new long[size];
            var newDestToSource = new long[size];

            int i = 0;
            while (loader.next()) {
                long source = loader.getSource();
                long dest = loader.getDest();

                newSourceToDest[i] = (source << 32) | dest;
                newDestToSource[i] = (dest << 32) | source;

                i++;
            }

            Arrays.sort(newSourceToDest);
            Arrays.sort(newDestToSource);

            sourceToDest = newSourceToDest;
            destToSource = newDestToSource;
        }
    }

    @Override
    public TIntArrayList findDestinations(int source) {
        return findRelated(sourceToDest, source);
    }

    @Override
    public TIntArrayList findSources(int dest) {
        return findRelated(destToSource, dest);
    }

    @Override
    public int countDestinations(int source) {
        return countRelated(sourceToDest, source);
    }

    @Override
    public int countSources(int dest) {
        return countRelated(destToSource, dest);
    }

    @Override
    public void forEach(SourceDestConsumer consumer) {
        for (long val : sourceToDest) {
            consumer.accept((int) (val >>> 32), (int) (val & 0xFFFF_FFFFL));
        }
    }

    private TIntArrayList findRelated(long[] range, int key) {
        long keyLong = Integer.toUnsignedLong(key) << 32;
        long nextKeyLong = Integer.toUnsignedLong(key + 1) << 32;

        int start = Arrays.binarySearch(range, keyLong);

        if (start < 0) {
            // Key is not found, get the insertion point
            start = -start - 1;
        }

        TIntArrayList result = new TIntArrayList();

        for (int i = start; i < range.length && range[i] < nextKeyLong; i++) {
            result.add((int) (range[i] & 0xFFFF_FFFFL));
        }

        return result;
    }

    private int countRelated(long[] range, int key) {
        long keyLong = Integer.toUnsignedLong(key) << 32;
        long nextKeyLong = Integer.toUnsignedLong(key + 1) << 32;

        int start = Arrays.binarySearch(range, keyLong);

        if (start < 0) {
            // Key is not found, get the insertion point
            start = -start - 1;
        }

        int num = 0;
        for (int i = start; i < range.length && range[i] < nextKeyLong; i++, num++);
        return num;
    }

}
