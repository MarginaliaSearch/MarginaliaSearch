package nu.marginalia.converting.model;

import gnu.trove.list.array.TLongArrayList;
import lombok.Getter;
import lombok.ToString;
import nu.marginalia.model.crawl.EdgePageWordFlags;

import java.util.*;
import java.util.function.UnaryOperator;

@ToString @Getter
public class DocumentKeywordsBuilder {
    public final ArrayList<String> words = new ArrayList<>();
    public final TLongArrayList metadata = new TLongArrayList();
    public DocumentKeywordsBuilder() {
    }

    public DocumentKeywords build() {
        return new DocumentKeywords(this);
    }

    public DocumentKeywordsBuilder(int cacpacity) {
        words.ensureCapacity(cacpacity);
        metadata.ensureCapacity(cacpacity);
    }

    public DocumentKeywordsBuilder(Collection<Entry> initial) {

        words.ensureCapacity(initial.size());
        metadata.ensureCapacity(initial.size());
        for (var entry : initial) {
            words.add(entry.word);
            metadata.add(entry.metadata);
        }
    }

    public static DocumentKeywordsBuilder withBlankMetadata(List<String> entries) {
        List<Long> emptyMeta = new ArrayList<>(entries.size());

        for (int i = 0; i < entries.size(); i++) {
            emptyMeta.add(0L);
        }

        return new DocumentKeywordsBuilder(entries, emptyMeta);
    }

    public void addJustNoMeta(String word) {
        words.add(word);
        metadata.add(0);
    }

    private DocumentKeywordsBuilder(List<String> words, List<Long> meta) {

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
