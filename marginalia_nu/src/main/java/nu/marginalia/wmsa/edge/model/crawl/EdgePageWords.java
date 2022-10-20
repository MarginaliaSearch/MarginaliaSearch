package nu.marginalia.wmsa.edge.model.crawl;

import gnu.trove.list.array.TLongArrayList;
import lombok.Getter;
import lombok.ToString;
import nu.marginalia.wmsa.edge.index.model.EdgePageWordFlags;
import nu.marginalia.wmsa.edge.index.model.EdgePageWordMetadata;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@ToString @Getter
public class EdgePageWords{
    public final IndexBlock block;
    public final ArrayList<String> words = new ArrayList<>();
    public final TLongArrayList metadata = new TLongArrayList();

    public EdgePageWords(IndexBlock block) {
        this.block = block;
    }
    public EdgePageWords(IndexBlock block, Collection<Entry> initial) {
        this.block = block;

        words.ensureCapacity(initial.size());
        metadata.ensureCapacity(initial.size());
        for (var entry : initial) {
            words.add(entry.word);
            metadata.add(entry.metadata);
        }
    }

    public static EdgePageWords withBlankMetadata(IndexBlock block, List<String> entries) {
        List<Long> emptyMeta = new ArrayList<>(entries.size());

        for (int i = 0; i < entries.size(); i++) {
            emptyMeta.add(EdgePageWordMetadata.emptyValue());
        }

        return new EdgePageWords(block, entries, emptyMeta);
    }

    public void addJustNoMeta(String word) {
        words.add(word);
        metadata.add(0);
    }

    private EdgePageWords(IndexBlock block, List<String> words, List<Long> meta) {
        this.block = block;

        this.words.addAll(words);
        this.metadata.addAll(meta);
    }

    public void addAll(Collection<Entry> newWords) {
        words.ensureCapacity(words.size() + newWords.size());
        metadata.ensureCapacity(metadata.size() + newWords.size());

        for (var entry : newWords) {
            words.add(entry.word);
            metadata.add(entry.metadata);
        }
    }

    public void setFlagOnMetadataForWords(EdgePageWordFlags flag, Set<String> flagWords) {
        for (int i = 0; i < words.size(); i++) {
            if (flagWords.contains(words.get(i))) {
                metadata.set(i, metadata.get(i) | flag.asBit());
            }
        }
    }

    public void addAllNoMeta(Collection<String> newWords) {
        words.ensureCapacity(words.size() + newWords.size());
        metadata.ensureCapacity(metadata.size() + newWords.size());

        for (var entry : newWords) {
            words.add(entry);
            metadata.add(0L);
        }
    }

    public int size() {
        return words.size();
    }

    public record Entry(String word, long metadata) {
    }
}
