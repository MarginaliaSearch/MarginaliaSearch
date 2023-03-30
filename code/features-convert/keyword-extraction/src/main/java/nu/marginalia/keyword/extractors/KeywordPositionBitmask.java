package nu.marginalia.keyword.extractors;

import com.google.inject.Inject;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import nu.marginalia.keyword.KeywordExtractor;
import nu.marginalia.language.model.DocumentLanguageData;

/** Generates a position bitmask for each word in a document */
public class KeywordPositionBitmask {
    private final Object2IntOpenHashMap<String> positionMask = new Object2IntOpenHashMap<>(10_000, 0.7f);

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

            int posBit = (int)((1L << linePos.pos()) & 0xFFFF_FFFFL);

            for (var word : sent) {
                positionMask.merge(word.stemmed(), posBit, this::bitwiseOr);
            }

            for (var span : keywordExtractor.getProperNames(sent)) {
                positionMask.merge(sent.constructStemmedWordFromSpan(span), posBit, this::bitwiseOr);
            }

            linePos.next(sent.length());
        }
    }

    public int get(String stemmed) {
        return positionMask.getOrDefault(stemmed, 0);
    }

    private int bitwiseOr(int a, int b) {
        return a | b;
    }

    private static class LinePosition {
        private int lineLengthCtr = 0;
        private int line = 0;
        private int bitMaskPos = 1;

        public int pos() {
            return bitMaskPos;
        }

        public void next(int sentenceLength) {
            if (bitMaskPos < 4) bitMaskPos++;
            else if (bitMaskPos < 8) {
                if (advanceLine(sentenceLength)>= 2) {
                    bitMaskPos++;
                    line = 0;
                }
            }
            else if (bitMaskPos < 24) {
                if (advanceLine(sentenceLength) >= 4) {
                    bitMaskPos++;
                    line = 0;
                }
            }
            else if (bitMaskPos < 64) {
                if (advanceLine(sentenceLength) > 8) {
                    bitMaskPos++;
                    line = 0;
                }
            }
        }

        private int advanceLine(int sentenceLength) {
            if (sentenceLength > 10) {
                lineLengthCtr = 0;
                return ++line;
            }

            lineLengthCtr += sentenceLength;
            if (lineLengthCtr > 15) {
                lineLengthCtr = 0;
                return ++line;
            }

            return line;
        }
    }
}
