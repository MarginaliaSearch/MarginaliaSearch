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

    // |------64 letters is this long-------------------------------|
    // granted, some of these words are word n-grams, but 64 ought to
    // be plenty. The lexicon writer has another limit that's higher.
    private final int MAX_WORD_LENGTH = 64;

    public DocumentKeywordsBuilder() {
        this(1600);
    }

    public DocumentKeywords build() {
        return new DocumentKeywords(this);
    }

    public DocumentKeywordsBuilder(int cacpacity) {
        words.ensureCapacity(cacpacity);
        metadata.ensureCapacity(cacpacity);
    }

    public void add(String word, long meta) {
        if (word.length() > MAX_WORD_LENGTH)
            return;

        words.add(word);
        metadata.add(meta);
    }

    public void addJustNoMeta(String word) {
        if (word.length() > MAX_WORD_LENGTH)
            return;

        words.add(word);
        metadata.add(0);
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

    public int size() {
        return words.size();
    }

    public void internalize(UnaryOperator<String> internalizer) {
        words.replaceAll(internalizer);
    }

}
