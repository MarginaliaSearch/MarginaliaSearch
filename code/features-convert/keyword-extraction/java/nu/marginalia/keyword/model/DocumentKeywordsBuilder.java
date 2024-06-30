package nu.marginalia.keyword.model;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import lombok.Getter;
import nu.marginalia.model.idx.WordFlags;
import nu.marginalia.model.idx.WordMetadata;
import nu.marginalia.sequence.GammaCodedSequence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.*;

@Getter
public class DocumentKeywordsBuilder {
    public final Object2LongLinkedOpenHashMap<String> wordToMeta;
    public final HashMap<String, IntList> wordToPos;

    /** These ware keywords that had signals of high relevance */
    public final Set<String> importantWords = new HashSet<>();

    // |------64 letters is this long-------------------------------|
    // granted, some of these words are word n-grams, but 64 ought to
    // be plenty. The lexicon writer has another limit that's higher.
    private final int MAX_WORD_LENGTH = 64;
    private final int MAX_POSITIONS_PER_WORD = 256;

    private static final Logger logger = LoggerFactory.getLogger(DocumentKeywordsBuilder.class);

    public DocumentKeywordsBuilder() {
        this(1600);
    }

    public DocumentKeywords build(ByteBuffer workArea) {
        final String[] wordArray = new String[wordToMeta.size()];
        final long[] meta = new long[wordToMeta.size()];
        final GammaCodedSequence[] positions = new GammaCodedSequence[wordToMeta.size()];

        var iter = wordToMeta.object2LongEntrySet().fastIterator();

        for (int i = 0; iter.hasNext(); i++) {
            var entry = iter.next();

            meta[i] = entry.getLongValue();
            wordArray[i] = entry.getKey();

            var posList = wordToPos.getOrDefault(entry.getKey(), IntList.of());

            if (posList.size() > MAX_POSITIONS_PER_WORD) {
                logger.info("Truncating positions for word '{}', count was {}", entry.getKey(), posList.size());
                posList.subList(MAX_POSITIONS_PER_WORD, posList.size()).clear();
            }

            positions[i] = GammaCodedSequence.generate(workArea, posList);
        }

        return new DocumentKeywords(wordArray, meta, positions);
    }

    public DocumentKeywordsBuilder(int capacity) {
        wordToMeta  = new Object2LongLinkedOpenHashMap<>(capacity);
        wordToPos = new HashMap<>(capacity);
    }

    public void addMeta(String word, long meta) {
        if (word.length() > MAX_WORD_LENGTH)
            return;

        wordToMeta.put(word, meta);
    }

    public void addPos(String word, int pos) {
        if (word.length() > MAX_WORD_LENGTH)
            return;

        wordToPos.computeIfAbsent(word, k -> new IntArrayList()).add(pos);
    }

    public void addImportantWords(Collection<String> words) {
        importantWords.addAll(words);
    }

    public void setFlagOnMetadataForWords(WordFlags flag, Collection<String> flagWords) {
        flagWords.forEach(word ->
            wordToMeta.mergeLong(word, flag.asBit(), (a, b) -> a|b)
        );
    }

    public void addAllSyntheticTerms(Collection<String> newWords) {
        long meta = WordFlags.Synthetic.asBit();

        // Only add the synthetic flag if the words aren't already present

        newWords.forEach(word -> wordToMeta.putIfAbsent(word, meta));
    }

    public void addAnchorTerms(Map<String, Integer> keywords) {
        long flagA = WordFlags.ExternalLink.asBit();
        long flagB = flagA | WordFlags.Site.asBit();
        long flagC = flagB | WordFlags.SiteAdjacent.asBit();

        keywords.forEach((word, count) -> {
            if (count > 5) {
                wordToMeta.mergeLong(word, flagC, (a, b) -> a|b);
            } else if (count > 2) {
                wordToMeta.mergeLong(word, flagB, (a, b) -> a|b);
            } else {
                wordToMeta.mergeLong(word, flagA, (a, b) -> a|b);
            }
        });
    }

    public List<String> getWordsWithAnyFlag(long flags) {
        List<String> ret = new ArrayList<>();

        for (var iter = wordToMeta.object2LongEntrySet().fastIterator(); iter.hasNext();) {
            var entry = iter.next();
            if ((flags & entry.getLongValue()) != 0) {
                ret.add(entry.getKey());
            }
        }

        return ret;
    }

    public int size() {
        return Math.max(wordToMeta.size(), wordToPos.size());
    }

    public WordMetadata getMetaForWord(String word) {
        return new WordMetadata(wordToMeta.getLong(word));
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[ ");
        wordToMeta.forEach((word, meta) -> {
            sb.append(word).append("->").append(new WordMetadata(meta).flagSet()).append(',').append(wordToPos.getOrDefault(word, new IntArrayList())).append(' ');
        });
        return sb.append(']').toString();
    }

}
