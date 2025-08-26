package nu.marginalia.language.pos;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import nu.marginalia.language.model.DocumentSentence;
import nu.marginalia.language.model.WordSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.OptionalInt;

public class PosPattern {
    public final LongArrayList pattern = new LongArrayList();
    private static final Logger logger = LoggerFactory.getLogger(PosPattern.class);

    public long[] toArray() {
        return pattern.toLongArray();
    }

    public int size() {
        return pattern.size();
    }

    public PosPattern(PosTagger taggingData, String expression) {
        List<String> tokens = new ArrayList<>();
        int pos = 0;

        while (pos < expression.length()) {
            char c = expression.charAt(pos);
            if ("()!".indexOf(c) >= 0) {
                tokens.add(expression.substring(pos, pos + 1));
                pos++;
            }
            else if (Character.isSpaceChar(c)) {
                pos++;
            }
            else {
                int end =  pos + 1;
                while (end <  expression.length()) {
                    int ce = expression.charAt(end);
                    if ("() ".indexOf(ce) >= 0) {
                        break;
                    }
                    else {
                        end++;
                    }
                }
                tokens.add(expression.substring(pos, end));
                pos = end;
            }
        }

        List<List<String>> variantsPerPos = new ArrayList<>();
        boolean inParen = false;
        boolean inverted = false;
        for (String token : tokens) {
            if ("!".equalsIgnoreCase(token)) {
                inverted = true;
            }
            else if ("(".equalsIgnoreCase(token)) {
                inParen = true;
                variantsPerPos.addLast(new ArrayList<>());
                if (inverted) {
                    variantsPerPos.getLast().addAll(taggingData.tags());
                }
            }
            else if (")".equals(token)) {
                inverted = false;
                inParen = false;
            }
            else if (!inParen) {
                if (inverted) {
                    List<String> allButToken = new ArrayList<>(taggingData.tags());
                    allButToken.remove(token);
                    variantsPerPos.addLast(allButToken);
                }
                else {
                    variantsPerPos.addLast(List.of(token));
                }
                inverted = false;
            }
            else if (inParen) {
                if (inverted) {
                    variantsPerPos.getLast().remove(token);
                }
                else {
                    variantsPerPos.getLast().add(token);
                }
            }
        }

        for (List<String> variants : variantsPerPos) {
            long variantsCompiled = 0L;
            for (String variant : variants) {
                if (variant.endsWith("*")) {
                    String prefix = variant.substring(0, variant.length() - 1);
                    for (int tagId : taggingData.tagIdsForPrefix(prefix)) {
                        variantsCompiled |= 1L << tagId;
                    }
                }
                else {
                    OptionalInt tag = taggingData.tagId(variant);
                    if (tag.isPresent()) {
                        variantsCompiled |= 1L << tag.getAsInt();
                    } else {
                        logger.warn("Pattern '{}' is referencing unknown POS tag '{}'", expression, variant);
                    }
                }
            }
            pattern.add(variantsCompiled);
        }
    }

    public void matchSentence(DocumentSentence sentence, List<WordSpan> ret) {
        pattern:
        for (int i = 0; i <= sentence.length() - pattern.size(); i++) {
            int j;

            int nextCommaPos = sentence.nextCommaPos(i);
            if (nextCommaPos < i + pattern.size() - 1) {
                i = nextCommaPos;
                continue;
            }

            for (j = 0; j < pattern.size() - 1; j++) {
                if (0L == (sentence.posTags[i+j] & pattern.getLong(j)))
                    continue pattern;
            }
            if (0L != (sentence.posTags[i + j] & pattern.getLong(j)))
                ret.add(new WordSpan(i, i+j+1));
        }
    }

    public boolean isMatch(DocumentSentence sentence, int pos) {
        if (pos + pattern.size() > sentence.length()) {
            return false;
        }

        int nextCommaPos = sentence.nextCommaPos(pos);
        if (nextCommaPos < pattern.size() - 1) {
            return false;
        }

        int j;
        for (j = 0; j < pattern.size() - 1; j++) {
            if (0L == (sentence.posTags[pos+j] & pattern.getLong(j)))
                return false;
        }
        return (0L != (sentence.posTags[pos + j]));
    }

    public BitSet matchTagPattern(long[] tags) {
        BitSet bs = new BitSet(tags.length);
        pattern:
        for (int i = 0; i <= tags.length - pattern.size(); i++) {
            int j;

            for (j = 0; j < pattern.size() - 1; j++) {
                if (0L == (tags[i+j] & pattern.getLong(j)))
                    continue pattern;
            }

            if (0L != (tags[i + j] & pattern.getLong(j))) {
                bs.set(i, i+pattern.size());
            }
        }

        return bs;
    }
}
