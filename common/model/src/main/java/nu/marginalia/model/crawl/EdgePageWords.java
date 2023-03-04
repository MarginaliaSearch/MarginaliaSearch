package nu.marginalia.model.crawl;

import gnu.trove.list.array.TLongArrayList;
import lombok.Getter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

@ToString @Getter
public class EdgePageWords {
    public final ArrayList<String> words = new ArrayList<>();
    public final TLongArrayList metadata = new TLongArrayList();

    public EdgePageWords() {
    }

    public EdgePageWords(int cacpacity) {
        words.ensureCapacity(cacpacity);
        metadata.ensureCapacity(cacpacity);
    }

    public EdgePageWords(Collection<Entry> initial) {

        words.ensureCapacity(initial.size());
        metadata.ensureCapacity(initial.size());
        for (var entry : initial) {
            words.add(entry.word);
            metadata.add(entry.metadata);
        }
    }

    public static EdgePageWords withBlankMetadata(List<String> entries) {
        List<Long> emptyMeta = new ArrayList<>(entries.size());

        for (int i = 0; i < entries.size(); i++) {
            emptyMeta.add(0L);
        }

        return new EdgePageWords(entries, emptyMeta);
    }

    public void addJustNoMeta(String word) {
        words.add(word);
        metadata.add(0);
    }

    private EdgePageWords(List<String> words, List<Long> meta) {

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
        if (flagWords.isEmpty())
            return;

        for (int i = 0; i < words.size(); i++) {
            if (flagWords.contains(words.get(i))) {
                metadata.set(i, metadata.get(i) | flag.asBit());
            }
        }
    }

    public void addAllSyntheticTerms(Collection<String> newWords) {
        words.ensureCapacity(words.size() + newWords.size());
        metadata.ensureCapacity(metadata.size() + newWords.size());

        long meta = EdgePageWordFlags.Synthetic.asBit();

        for (var entry : newWords) {
            words.add(entry);
            metadata.add(meta);
        }
    }

    public List<String> getWordsWithAnyFlag(long flags) {
        List<String> ret = new ArrayList<>();

        for (int i = 0; i < words.size(); i++) {
            if ((metadata.get(i) & flags) > 0) {
                ret.add(words.get(i));
            }
        }

        return ret;
    }

    public void add(String word, long meta) {
        words.add(word);
        metadata.add(meta);
    }

    public int size() {
        return words.size();
    }

    public void internalize(UnaryOperator<String> internalizer) {
        words.replaceAll(internalizer);
    }

    public record Entry(String word, long metadata) {
    }
}
