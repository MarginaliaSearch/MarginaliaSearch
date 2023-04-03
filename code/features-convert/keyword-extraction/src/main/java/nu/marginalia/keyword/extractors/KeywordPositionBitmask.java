package nu.marginalia.keyword.extractors;

import com.google.inject.Inject;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import nu.marginalia.keyword.KeywordExtractor;
import nu.marginalia.language.model.DocumentLanguageData;

/** Generates a position bitmask for each word in a document */
public class KeywordPositionBitmask {
    private final Object2LongOpenHashMap<String> positionMask = new Object2LongOpenHashMap<>(10_000, 0.7f);
    private final static int positionWidth = 56;
    private final static long positionBitmask = (1L << positionWidth) - 1;
    private static final int unmodulatedPortion = 16;

    @Inject
    public KeywordPositionBitmask(KeywordExtractor keywordExtractor, DocumentLanguageData dld) {

        // Mark the title words as position 0
        for (var sent : dld.titleSentences) {
            int posBit = 1;

            for (var word : sent) {
                positionMask.merge(word.stemmed(), posBit, this::bitwiseOr);
            }

            for (var span : keywordExtractor.getProperNames(sent)) {
                positionMask.merge(sent.constructStemmedWordFromSpan(span), posBit, this::bitwiseOr);
            }
        }

        // Mark subsequent sentences in subsequent positions, with increasing sentence step size
        LinePosition linePos = new LinePosition();
        for (var sent : dld.sentences) {

            long posBit = (1L << linePos.pos()) & positionBitmask;

            for (var word : sent) {
                positionMask.merge(word.stemmed(), posBit, this::bitwiseOr);
            }

            for (var span : keywordExtractor.getProperNames(sent)) {
                positionMask.merge(sent.constructStemmedWordFromSpan(span), posBit, this::bitwiseOr);
            }

            linePos.next(sent.length());
        }
    }

    public long get(String stemmed) {
        return positionMask.getOrDefault(stemmed, 0);
    }

    private long bitwiseOr(long a, long b) {
        return a | b;
    }

    private static class LinePosition {
        private int lineLengthCtr = 0;
        private int bitMaskPos = 1;

        public int pos() {
            if (bitMaskPos < unmodulatedPortion) {
                return bitMaskPos;
            }
            else {
                return unmodulatedPortion + ((bitMaskPos - unmodulatedPortion) % (positionWidth - unmodulatedPortion));
            }
        }

        public void next(int sentenceLength)
        {
            if (sentenceLength > 10) {
                lineLengthCtr = 0;
                ++bitMaskPos;
            }

            lineLengthCtr += sentenceLength;
            if (lineLengthCtr > 15) {
                lineLengthCtr = 0;
                ++bitMaskPos;
            }

        }

    }
}
