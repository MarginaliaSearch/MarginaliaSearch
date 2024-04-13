package nu.marginalia.segmentation;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import it.unimi.dsi.fastutil.longs.Long2IntOpenCustomHashMap;
import it.unimi.dsi.fastutil.longs.LongHash;
import nu.marginalia.LanguageModels;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Singleton
public class NgramLexicon {
    private final Long2IntOpenCustomHashMap counts;

    private int size;
    private static final HasherGroup orderedHasher = HasherGroup.ordered();

    @Inject
    public NgramLexicon(LanguageModels models) {
        try (var dis = new DataInputStream(new BufferedInputStream(Files.newInputStream(models.segments)))) {
            long size = dis.readInt();
            counts = new Long2IntOpenCustomHashMap(
                    (int) size,
                    new KeyIsAlreadyHashStrategy()
            );
            counts.defaultReturnValue(0);

            try {
                for (int i = 0; i < size; i++) {
                    counts.put(dis.readLong(), dis.readInt());
                }
            }
            catch (IOException ex) {
                ex.printStackTrace();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public NgramLexicon() {
        counts = new Long2IntOpenCustomHashMap(100_000_000, new KeyIsAlreadyHashStrategy());
        counts.defaultReturnValue(0);
    }

    public List<String[]> findSegmentsStrings(int minLength,
                                              int maxLength,
                                              String... parts)
    {
        List<String[]> segments = new ArrayList<>();

        // Hash the parts
        long[] hashes = new long[parts.length];
        for (int i = 0; i < hashes.length; i++) {
            hashes[i] = HasherGroup.hash(parts[i]);
        }

        for (int i = minLength; i <= maxLength; i++) {
            findSegments(segments, i, parts, hashes);
        }

        return segments;
    }
    
    public void findSegments(List<String[]> positions,
                             int length,
                             String[] parts,
                             long[] hashes)
    {
        // Don't look for ngrams longer than the sentence
        if (parts.length < length) return;

        long hash = 0;
        int i = 0;

        // Prepare by combining up to length hashes
        for (; i < length; i++) {
            hash = orderedHasher.apply(hash, hashes[i]);
        }

        // Slide the window and look for matches
        for (;;) {
            if (counts.get(hash) > 0) {
                positions.add(Arrays.copyOfRange(parts, i - length, i));
            }

            if (i < hashes.length) {
                hash = orderedHasher.replace(hash, hashes[i], hashes[i - length], length);
                i++;
            } else {
                break;
            }
        }
    }

    public List<SentenceSegment> findSegmentOffsets(int length, String... parts) {
        // Don't look for ngrams longer than the sentence
        if (parts.length < length) return List.of();

        List<SentenceSegment> positions = new ArrayList<>();

        // Hash the parts
        long[] hashes = new long[parts.length];
        for (int i = 0; i < hashes.length; i++) {
            hashes[i] = HasherGroup.hash(parts[i]);
        }

        long hash = 0;
        int i = 0;

        // Prepare by combining up to length hashes
        for (; i < length; i++) {
            hash = orderedHasher.apply(hash, hashes[i]);
        }

        // Slide the window and look for matches
        for (;;) {
            int ct = counts.get(hash);

            if (ct > 0) {
                positions.add(new SentenceSegment(i - length, length, ct));
            }

            if (i < hashes.length) {
                hash = orderedHasher.replace(hash, hashes[i], hashes[i - length], length);
                i++;
            } else {
                break;
            }
        }

        return positions;
    }

    public void incOrderedTitle(long hashOrdered) {
        int value = counts.get(hashOrdered);

        if (value <= 0) {
            size ++;
            value = -value;
        }

        value ++;

        counts.put(hashOrdered, value);
    }

    public void incOrderedBody(long hashOrdered) {
        int value = counts.get(hashOrdered);

        if (value <= 0) value --;
        else value++;

        counts.put(hashOrdered, value);
    }

    public void saveCounts(Path file) throws IOException {
        try (var dos = new DataOutputStream(Files.newOutputStream(file,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE))) {

            dos.writeInt(size);

            counts.forEach((k, v) -> {
                try {
                    if (v > 0) {
                        dos.writeLong(k);
                        dos.writeInt(v);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    public void clear() {
        counts.clear();
    }

    public record SentenceSegment(int start, int length, int count) {
        public String[] project(String... parts) {
            return Arrays.copyOfRange(parts, start, start + length);
        }

        public boolean overlaps(SentenceSegment other) {
            return start < other.start + other.length && start + length > other.start;
        }
    }

    private static class KeyIsAlreadyHashStrategy implements LongHash.Strategy {
        @Override
        public int hashCode(long l) {
            return (int) l;
        }

        @Override
        public boolean equals(long l, long l1) {
            return l == l1;
        }
    }

}

