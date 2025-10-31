package nu.marginalia.keyword.model;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntComparator;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import nu.marginalia.language.sentence.tag.HtmlTag;
import nu.marginalia.model.idx.WordFlags;
import nu.marginalia.sequence.VarintCodedSequence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class DocumentKeywordsBuilder {
    public final Object2LongOpenHashMap<String> wordToMeta;
    public final HashMap<String, IntList> wordToPos;
    public final Map<HtmlTag, List<DocumentWordSpan>> wordSpans = new HashMap<>();

    /**
     * These ware keywords that had signals of high relevance
     */
    public final Set<String> importantWords = new HashSet<>();

    // |------64 letters is this long-------------------------------|
    // granted, some of these words are word n-grams, but 64 ought to
    // be plenty. The lexicon writer has another limit that's higher.
    static final int MAX_WORD_LENGTH = 64;
    static final int MAX_POSITIONS_PER_WORD = 512;
    static final int MAX_SPANS_PER_TYPE = 8192;
    static final int POSITIONS_BITMASK_WINDOW_SIZE = 256;

    private static final Logger logger = LoggerFactory.getLogger(DocumentKeywordsBuilder.class);

    public DocumentKeywordsBuilder() {
        this(1600);
    }

    public DocumentKeywordsBuilder(int capacity) {
        wordToMeta = new Object2LongOpenHashMap<>(capacity);
        wordToPos = new HashMap<>(capacity);
    }


    public DocumentKeywords build() {
        List<String> wordArray = new ArrayList<>(wordToMeta.size());
        LongArrayList meta = new LongArrayList(wordToMeta.size());
        List<VarintCodedSequence> positions = new ArrayList<>(wordToMeta.size());
        List<VarintCodedSequence> spanSequences = new ArrayList<>(wordSpans.size());
        byte[] spanCodes = new byte[wordSpans.size()];

        var iter = wordToMeta.object2LongEntrySet().fastIterator();

        // Encode positions
        while (iter.hasNext()) {
            Object2LongMap.Entry<String> entry = iter.next();

            wordArray.add(entry.getKey());

            // Truncate and encode exact positions
            IntList posList = wordToPos.getOrDefault(entry.getKey(), IntList.of());
            if (posList.size() > MAX_POSITIONS_PER_WORD) {
                posList.subList(MAX_POSITIONS_PER_WORD, posList.size()).clear();
            }
            positions.add(VarintCodedSequence.generate(posList));

            // Construct a positions bit mask and add it to bits 8 - 64 in the term metadata
            meta.add(calculatePositionMask(entry.getLongValue(), posList));
        }

        // Reorganize the word-level information in a way we are more likely to be able to
        // perform efficient disk reads later

        IntList sortOrder = new IntArrayList(wordArray.size());
        for (int i = 0; i < wordArray.size(); i++) sortOrder.add(i);
        sortOrder.sort(new SortOrderComparator(positions, meta));

        wordArray = reorderList(wordArray, sortOrder);
        meta = reorderList(meta, sortOrder);
        positions = reorderList(positions, sortOrder);

        // Encode spans
        wordSpans.forEach((tag, spansForTag) -> {
            spansForTag.sort(Comparator.comparingInt(DocumentWordSpan::start));

            var positionsForTag = new IntArrayList(spansForTag.size() * 2);

            for (var span : spansForTag) {
                positionsForTag.add(span.start());
                positionsForTag.add(span.end());

                if (positionsForTag.size() >= MAX_SPANS_PER_TYPE)
                    break;
            }

            spanCodes[spanSequences.size()] = tag.code;
            spanSequences.add(VarintCodedSequence.generate(positionsForTag));
        });

        return new DocumentKeywords(wordArray, meta.toLongArray(), positions, spanCodes, spanSequences);
    }

    static class SortOrderComparator implements IntComparator {

        private final List<VarintCodedSequence> positions;
        private final LongArrayList meta;

        public SortOrderComparator(List<VarintCodedSequence> positions, LongArrayList meta) {
            this.positions = positions;
            this.meta = meta;
        }

        @Override
        public int compare(int k1, int k2) {
            int sizeCmp = Integer.compare(positions.get(k2).bufferSize(), positions.get(k1).bufferSize());
            if (sizeCmp != 0) return sizeCmp;

            long meta1 = meta.getLong(k1) & 0xFF;
            long meta2 = meta.getLong(k2 & 0xFF);

            return Integer.compare(Long.bitCount(meta2), Long.bitCount(meta1));
        }

    }

    private <T> List<T> reorderList(List<T> source, IntList order) {
        List<T> ret = new ArrayList<>(source.size());
        for (int i = 0; i < order.size(); i++) {
            ret.add(source.get(order.getInt(i)));
        }
        return ret;
    }

    private LongArrayList reorderList(LongArrayList source, IntList order) {
        LongArrayList ret = new LongArrayList(source.size());
        for (int i = 0; i < order.size(); i++) {
            ret.add(source.getLong(order.getInt(i)));
        }
        return ret;
    }

    long calculatePositionMask(long termMeta, IntList positions) {
        long ret = termMeta;

        for (int i = 0; i < positions.size(); i++) {
            int pos = positions.getInt(i);
            int bit = (pos / POSITIONS_BITMASK_WINDOW_SIZE) % 56;
            ret |= 1L << (8 + bit);

            // Also flag the next bit if we are past the half-way point to make the mask a bit more lenient to
            // rounding errors; we actually want (actually) adjacent words to overlap on the same bit
            bit = ((pos + POSITIONS_BITMASK_WINDOW_SIZE / 2) / POSITIONS_BITMASK_WINDOW_SIZE) % 56;
            ret |= 1L << (8 + bit);
        }

        return ret;
    }

    public void addMeta(String word, byte meta) {
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
                wordToMeta.mergeLong(word, flag.asBit(), (a, b) -> (byte) (a | b))
        );
    }

    public void addAllSyntheticTerms(Collection<String> newWords) {
        byte meta = WordFlags.Synthetic.asBit();

        // Only add the synthetic flag if the words aren't already present

        newWords.forEach(word -> wordToMeta.putIfAbsent(word, meta));
    }

    public void addSyntheticTerm(String newWord) {
        byte meta = WordFlags.Synthetic.asBit();

        wordToMeta.putIfAbsent(newWord, meta);
    }


    public List<String> getWordsWithAnyFlag(long flags) {
        List<String> ret = new ArrayList<>();

        for (var iter = wordToMeta.object2LongEntrySet().fastIterator(); iter.hasNext(); ) {
            var entry = iter.next();
            if ((flags & entry.getLongValue()) != 0) {
                ret.add(entry.getKey());
            }
        }

        return ret;
    }

    public void addSpans(List<DocumentWordSpan> newSpans) {
        for (var span : newSpans) {
            wordSpans.computeIfAbsent(span.tag(), k -> new ArrayList<>()).add(span);
        }
    }

    public int size() {
        return Math.max(wordToMeta.size(), wordToPos.size());
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[ ");

        wordToMeta.forEach((word, meta) -> {
            sb.append(word)
                    .append("->")
                    .append(WordFlags.decode((byte) meta.longValue()))
                    .append(',')
                    .append(wordToPos.getOrDefault(word, new IntArrayList()))
                    .append(' ');
        });

        wordSpans.forEach((tag, spans) -> {
            sb.append(tag)
                    .append("->")
                    .append(spans)
                    .append(' ');
        });
        return sb.append(']').toString();
    }

    public Object2LongOpenHashMap<String> getWordToMeta() {
        return this.wordToMeta;
    }

    public Set<String> getImportantWords() {
        return this.importantWords;
    }

}
