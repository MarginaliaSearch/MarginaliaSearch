package nu.marginalia.segmentation;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import it.unimi.dsi.fastutil.longs.Long2IntOpenCustomHashMap;
import it.unimi.dsi.fastutil.longs.LongHash;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
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

    private static final HasherGroup orderedHasher = HasherGroup.ordered();

    @Inject
    public NgramLexicon(LanguageModels models) {
        try (var dis = new DataInputStream(new BufferedInputStream(Files.newInputStream(models.segments)))) {
            long size = dis.readInt();
            counts = new Long2IntOpenCustomHashMap(
                    (int) size,
                    new KeyIsAlreadyHashStrategy()
            );

            for (int i = 0; i < size; i++) {
                counts.put(dis.readLong(), dis.readInt());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public NgramLexicon() {
        counts = new Long2IntOpenCustomHashMap(100_000_000, new KeyIsAlreadyHashStrategy());
    }

    public List<String[]> findSegmentsStrings(int minLength, int maxLength, String... parts) {
        List<String[]> segments = new ArrayList<>();

        for (int i = minLength; i <= maxLength; i++) {
            segments.addAll(findSegments(i, parts));
        }

        return segments;
    }
    
    public List<String[]> findSegments(int length, String... parts) {
        // Don't look for ngrams longer than the sentence
        if (parts.length < length) return List.of();

        List<String[]> positions = new ArrayList<>();

        // Hash the parts
        long[] hashes = new long[parts.length];
        for (int i = 0; i < hashes.length; i++) {
            hashes[i] = HasherGroup.hash(parts[i]);
        }

        long ordered = 0;
        int i = 0;

        // Prepare by combining up to length hashes
        for (; i < length; i++) {
            ordered = orderedHasher.apply(ordered, hashes[i]);
        }

        // Slide the window and look for matches
        for (;; i++) {
            int ct = counts.get(ordered);

            if (ct > 0) {
                positions.add(Arrays.copyOfRange(parts, i - length, length));
            }

            if (i >= hashes.length)
                break;

            // Remove the oldest hash and add the new one
            ordered = orderedHasher.replace(ordered,
                    hashes[i],
                    hashes[i - length],
                    length);
        }

        return positions;
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

        long ordered = 0;
        int i = 0;

        // Prepare by combining up to length hashes
        for (; i < length; i++) {
            ordered = orderedHasher.apply(ordered, hashes[i]);
        }

        // Slide the window and look for matches
        for (;; i++) {
            int ct = counts.get(ordered);

            if (ct > 0) {
                positions.add(new SentenceSegment(i - length, length, ct));
            }

            if (i >= hashes.length)
                break;

            // Remove the oldest hash and add the new one
            ordered = orderedHasher.replace(ordered,
                    hashes[i],
                    hashes[i - length],
                    length);
        }

        return positions;
    }

    public void incOrdered(long hashOrdered) {
        counts.addTo(hashOrdered, 1);
    }

    public void saveCounts(Path file) throws IOException {
        try (var dos = new DataOutputStream(Files.newOutputStream(file,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE))) {
            dos.writeInt(counts.size());

            counts.forEach((k, v) -> {
                try {
                    dos.writeLong(k);
                    dos.writeInt(v);
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

