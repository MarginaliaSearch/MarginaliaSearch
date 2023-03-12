package nu.marginalia.converting.processor.keywords.extractors;

import com.google.inject.Inject;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import nu.marginalia.language.keywords.KeywordExtractor;
import nu.marginalia.language.model.DocumentLanguageData;
import nu.marginalia.language.model.KeywordMetadata;

/** Generates a position bitmask for each word in a document */
public class DocumentKeywordPositionBitmaskExtractor {
    private final KeywordExtractor keywordExtractor;

    @Inject
    public DocumentKeywordPositionBitmaskExtractor(KeywordExtractor keywordExtractor) {
        this.keywordExtractor = keywordExtractor;
    }

    public KeywordMetadata getWordPositions(DocumentLanguageData dld) {
        final KeywordMetadata keywordMetadata = new KeywordMetadata();

        Object2IntOpenHashMap<String> ret = keywordMetadata.positionMask;

        // Mark the title words as position 0
        for (var sent : dld.titleSentences) {
            int posBit = 1;

            for (var word : sent) {
                ret.merge(word.stemmed(), posBit, this::bitwiseOr);
            }

            for (var span : keywordExtractor.getProperNames(sent)) {
                ret.merge(sent.constructStemmedWordFromSpan(span), posBit, this::bitwiseOr);
            }
        }

        // Mark subsequent sentences in subsequent positions, with increasing sentence step size
        LinePosition linePos = new LinePosition();
        for (var sent : dld.sentences) {

            int posBit = (int)((1L << linePos.pos()) & 0xFFFF_FFFFL);

            for (var word : sent) {
                ret.merge(word.stemmed(), posBit, this::bitwiseOr);
            }

            for (var span : keywordExtractor.getProperNames(sent)) {
                ret.merge(sent.constructStemmedWordFromSpan(span), posBit, this::bitwiseOr);
            }

            linePos.next();
        }

        return keywordMetadata;
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
