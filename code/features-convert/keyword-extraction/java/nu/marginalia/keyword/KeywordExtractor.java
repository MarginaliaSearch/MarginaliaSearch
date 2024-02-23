package nu.marginalia.keyword;

import nu.marginalia.language.WordPatterns;
import nu.marginalia.language.model.DocumentSentence;
import nu.marginalia.language.model.WordSpan;
import nu.marginalia.language.model.WordSeparator;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.List;

public class KeywordExtractor {

    public WordSpan[] getProperNames(DocumentSentence sentence) {
        List<WordSpan> spans = new ArrayList<>(2 * sentence.length());

        for (int i = 0; i < sentence.length(); i++) {
            if (isProperNoun(i, sentence))
                spans.add(new WordSpan(i, i+1));
        }

        for (int i = 1; i < sentence.length(); i++) {
            if (sentence.separators[i-1] == WordSeparator.COMMA) { continue; }

            if (isProperNoun(i, sentence) && isProperNoun(i-1, sentence))
                spans.add(new WordSpan(i-1, i+1));
        }

        for (int i = 2; i < sentence.length(); i++) {
            if (sentence.separators[i-2] == WordSeparator.COMMA) { continue; }
            if (sentence.separators[i-1] == WordSeparator.COMMA) { i++; continue; }

            if (isProperNoun(i, sentence)
                && (isJoiner(sentence, i-1) || isProperNoun(i-1, sentence))
                && isProperNoun(i-2, sentence))
                spans.add(new WordSpan(i-2, i+1));
        }

        for (int i = 3; i < sentence.length(); i++) {
            if (sentence.separators[i-3] == WordSeparator.COMMA) { continue; }
            if (sentence.separators[i-2] == WordSeparator.COMMA) { i++; continue; }
            if (sentence.separators[i-1] == WordSeparator.COMMA) { i+=2; continue; }

            if (isProperNoun(i, sentence) && isProperNoun(i-3, sentence)) {
                if (isProperNoun(i - 1, sentence) && isProperNoun(i - 2, sentence))
                    spans.add(new WordSpan(i-3, i+1));
                else if (isJoiner(sentence, i-2) && sentence.posTags[i-1].equals("DT"))
                    spans.add(new WordSpan(i-3, i+1));
                else if ((isJoiner(sentence, i-1) ||isProperNoun(i-1, sentence))
                      && (isJoiner(sentence, i-2)||isProperNoun(i-2, sentence)))
                    spans.add(new WordSpan(i-3, i+1));
            }
        }

        return spans.toArray(WordSpan[]::new);
    }


    public WordSpan[] getNouns(DocumentSentence sentence) {
        List<WordSpan> spans = new ArrayList<>(2 * sentence.length());

        for (int i = 0; i < sentence.length(); i++) {
            if (isNoun(i, sentence)) {
                spans.add(new WordSpan(i, i + 1));
            }
        }

        for (int i = 1; i < sentence.length(); i++) {
            if (sentence.separators[i-1] == WordSeparator.COMMA) { continue; }

            if (isNoun(i, sentence)
                    && (isNoun(i-1, sentence)) || "JJ".equals(sentence.posTags[i-1])) {
                spans.add(new WordSpan(i - 1, i + 1));
            }
        }

        for (int i = 2; i < sentence.length(); i++) {
            if (sentence.separators[i-2] == WordSeparator.COMMA) { continue; }
            if (sentence.separators[i-1] == WordSeparator.COMMA) { i++; continue; }

            if ((isNoun(i, sentence))
                    && (isJoiner(sentence, i-1) || isNoun(i-1, sentence))
                    && (isNoun(i-2, sentence)) || "JJ".equals(sentence.posTags[i-2]))
                spans.add(new WordSpan(i-2, i+1));
        }

        for (int i = 3; i < sentence.length(); i++) {
            if (sentence.separators[i-3] == WordSeparator.COMMA) { continue; }
            if (sentence.separators[i-2] == WordSeparator.COMMA) { i++; continue; }
            if (sentence.separators[i-1] == WordSeparator.COMMA) { i+=2; continue; }

            if (isNoun(i, sentence) && (isNoun(i-3, sentence) || "JJ".equals(sentence.posTags[i-3]))) {
                if (isNoun(i - 1, sentence) && isNoun(i - 2, sentence))
                    spans.add(new WordSpan(i-3, i+1));
                else if (isJoiner(sentence, i-2) && sentence.posTags[i-1].equals("DT"))
                    spans.add(new WordSpan(i-3, i+1));
                else if ((isJoiner(sentence, i-1) ||isNoun(i-1, sentence))
                        && (isJoiner(sentence, i-2)||isNoun(i-2, sentence)))
                    spans.add(new WordSpan(i-3, i+1));
            }
        }

        return spans.toArray(WordSpan[]::new);
    }


    public WordSpan[] getKeywordsFromSentence(DocumentSentence sentence) {
        if (sentence.keywords != null) {
            var maybeRet = sentence.keywords.get();
            if (maybeRet != null)
                return maybeRet;
        }

        List<WordSpan> spans = new ArrayList<>(2 * sentence.length());

        for (int i = 0; i < sentence.length(); i++) {
            if (isName(i, sentence) || isTopAdj(i, sentence))
                spans.add(new WordSpan(i, i+1));
        }

        for (int i = 1; i < sentence.length(); i++) {
            if (sentence.separators[i-1] == WordSeparator.COMMA) { continue; }

            if (isName(i, sentence)) {
                if (isName(i - 1, sentence) || isTopAdj(i-1, sentence))
                    spans.add(new WordSpan(i - 1, i + 1));
            }
            if (sentence.posTags[i].equals("CD") &&  isName(i-1, sentence)) {
                spans.add(new WordSpan(i - 1, i + 1));
            }
        }

        for (int i = 2; i < sentence.length(); i++) {
            if (sentence.separators[i-1] == WordSeparator.COMMA) { i++; continue; }
            if (sentence.separators[i-2] == WordSeparator.COMMA) { continue; }

            if (isName(i, sentence)) {
                if ((isName(i-1, sentence) || isTopAdj(i-1, sentence))
                        && (isName(i-2, sentence) || isTopAdj(i-2, sentence))) {
                    spans.add(new WordSpan(i - 2, i + 1));
                }
                else if ((isProperNoun(i-1, sentence) || isJoiner(sentence, i-1)) && isProperNoun(i-2, sentence)) {
                    spans.add(new WordSpan(i - 2, i + 1));
                }
            }
            else if (sentence.posTags[i].equals("CD") && isName(i-1, sentence) && isName(i-2, sentence)) {
                spans.add(new WordSpan(i - 2, i + 1));
            }
        }

        for (int i = 3; i < sentence.length(); i++) {
            if (sentence.separators[i-1] == WordSeparator.COMMA) { i+=2; continue; }
            if (sentence.separators[i-2] == WordSeparator.COMMA) { i++; continue; }
            if (sentence.separators[i-3] == WordSeparator.COMMA) { continue; }

            if (isName(i, sentence) &&
                    (isName(i-1, sentence) || isTopAdj(i-1, sentence)) &&
                    (isName(i-2, sentence) || isTopAdj(i-2, sentence)) &&
                    (isName(i-3, sentence) || isTopAdj(i-3, sentence))) {
                spans.add(new WordSpan(i - 3, i + 1));
            }
            else if (isProperNoun(i, sentence) && isProperNoun(i-3, sentence)) {
                if (isProperNoun(i - 1, sentence) && isProperNoun(i - 2, sentence)) {
                    spans.add(new WordSpan(i-3, i+1));
                }
                else if (isJoiner(sentence, i-1) && sentence.posTags[i-2].equals("DT")) {
                    spans.add(new WordSpan(i-3, i+1));
                }
                else if ((isProperNoun(i-1, sentence) || isJoiner(sentence, i-1)) && (isProperNoun(i-2, sentence)|| isJoiner(sentence, i-2))) {
                    spans.add(new WordSpan(i-3, i + 1));
                }
            }

        }

        var ret = spans.toArray(WordSpan[]::new);
        sentence.keywords = new SoftReference<>(ret);

        return ret;
    }

    public boolean isProperNoun(int i, DocumentSentence sent) {
        return "NNP".equals(sent.posTags[i]) || "NNPS".equals(sent.posTags[i]);
    }
    public boolean isNoun(int i, DocumentSentence sent) {
        return sent.posTags[i].startsWith("NN");
    }
    public boolean isJoiner(DocumentSentence sent, int i) {
        if(sent.posTags[i].equals("IN")) {
            return true;
        }
        if (sent.posTags[i].equals("TO")) {
            return true;
        }
        if (sent.posTags[i].equals("CC")) {
            return sent.wordsLowerCase[i].equals("and");
        }
        return false;
    }

    public List<WordSpan> getWordsFromSentence(DocumentSentence sentence) {
        List<WordSpan> spans = new ArrayList<>();

        for (int k = 0; k < 4; k++) {
            for (int i = k; i < sentence.length(); i++) {
                var w = new WordSpan(i-k, i + 1);

                if (isViableSpanForWord(sentence, w)) {
                    spans.add(w);
                }
            }
        }

        return spans;
    }

    private boolean isViableSpanForWord(DocumentSentence sentence, WordSpan w) {

        for (int i = w.start; i < w.end-1; i++) {
            if (sentence.separators[i] == WordSeparator.COMMA) {
                return false;
            }
        }
        String word = sentence.constructWordFromSpan(w);

        if (word.isBlank() || !WordPatterns.isNotJunkWord(word)) return false;
        if (sentence.posTags[w.start].equals("CC")) return false;
        if (sentence.posTags[w.end-1].equals("IN")) return false;
        if (sentence.posTags[w.end-1].equals("DT")) return false;
        if (sentence.posTags[w.end-1].equals("CC")) return false;
        if (sentence.posTags[w.end-1].equals("TO")) return false;

        return true;
    }

    private boolean isName(int i, DocumentSentence sentence) {
        String posTag = sentence.posTags[i];

        return (posTag.startsWith("N") || "VBG".equals(posTag)|| "VBN".equals(posTag)) && !sentence.isStopWord(i);
    }

    private boolean isTopAdj(int i, DocumentSentence sentence) {
        String posTag = sentence.posTags[i];

        return (posTag.startsWith("JJ") || posTag.startsWith("R") || posTag.startsWith("VBG"));
    }


}
