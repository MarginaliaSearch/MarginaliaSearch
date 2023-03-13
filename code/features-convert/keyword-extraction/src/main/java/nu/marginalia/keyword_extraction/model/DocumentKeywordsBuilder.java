package nu.marginalia.keyword_extraction.model;

import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import lombok.Getter;
import lombok.ToString;
import nu.marginalia.model.idx.WordFlags;

import java.util.*;

@ToString @Getter
public class DocumentKeywordsBuilder {
    public final Object2LongLinkedOpenHashMap<String> words;

    // |------64 letters is this long-------------------------------|
    // granted, some of these words are word n-grams, but 64 ought to
    // be plenty. The lexicon writer has another limit that's higher.
    private final int MAX_WORD_LENGTH = 64;

    public DocumentKeywordsBuilder() {
        this(1600);
    }

    public DocumentKeywords build() {
        final String[] wordArray = new String[words.size()];
        final long[] meta = new long[words.size()];

        var iter = words.object2LongEntrySet().fastIterator();

        for (int i = 0; iter.hasNext(); i++) {
            var entry = iter.next();

            meta[i] = entry.getLongValue();
            wordArray[i] = entry.getKey();
        }

        return new DocumentKeywords(wordArray, meta);
    }

    public DocumentKeywordsBuilder(int cacpacity) {
        words  = new Object2LongLinkedOpenHashMap<>(cacpacity);
    }

    public void add(String word, long meta) {
        if (word.length() > MAX_WORD_LENGTH)
            return;

        words.put(word, meta);
    }

    public void addJustNoMeta(String word) {
        if (word.length() > MAX_WORD_LENGTH)
            return;

        words.putIfAbsent(word, 0);
    }

    public void setFlagOnMetadataForWords(WordFlags flag, Set<String> flagWords) {
        flagWords.forEach(word ->
            words.mergeLong(word, flag.asBit(), (a, b) -> a|b)
        );
    }

    public void addAllSyntheticTerms(Collection<String> newWords) {
        long meta = WordFlags.Synthetic.asBit();

        // Only add the synthetic flag if the words aren't already present

        newWords.forEach(word -> words.putIfAbsent(word, meta));
    }

    public List<String> getWordsWithAnyFlag(long flags) {
        List<String> ret = new ArrayList<>();

        for (var iter = words.object2LongEntrySet().fastIterator(); iter.hasNext();) {
            var entry = iter.next();
            if ((flags & entry.getLongValue()) != 0) {
                ret.add(entry.getKey());
            }
        }

        return ret;
    }

    public int size() {
        return words.size();
    }

}
