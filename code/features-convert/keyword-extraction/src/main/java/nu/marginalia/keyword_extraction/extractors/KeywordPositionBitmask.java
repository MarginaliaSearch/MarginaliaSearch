package nu.marginalia.keyword_extraction.extractors;

import com.google.inject.Inject;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import nu.marginalia.keyword_extraction.KeywordExtractor;
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

            linePos.next();
        }
    }

    public int get(String stemmed) {
        return positionMask.getOrDefault(stemmed, 0);
    }

    private int bitwiseOr(int a, int b) {
        return a | b;
    }

    private static class LinePosition {
        private int line = 0;
        private int pos = 1;

        public int pos() {
            return pos;
        }

        public void next() {
            if (pos < 4) pos ++;
            else if (pos < 8) {
                if (++line >= 2) {
                    pos++;
                    line = 0;
                }
            }
            else if (pos < 24) {
                if (++line >= 4) {
                    pos++;
                    line = 0;
                }
            }
            else if (pos < 64) {
                if (++line > 8) {
                    pos++;
                    line = 0;
                }
            }
        }
    }
}
