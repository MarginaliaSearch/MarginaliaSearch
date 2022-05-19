package nu.marginalia.wmsa.edge.crawler.domain.language.processing;

import nu.marginalia.wmsa.edge.crawler.domain.language.WordPatterns;
import nu.marginalia.wmsa.edge.crawler.domain.language.processing.model.DocumentSentence;
import nu.marginalia.wmsa.edge.crawler.domain.language.processing.model.tag.WordSeparator;
import nu.marginalia.wmsa.edge.crawler.domain.language.processing.model.WordSpan;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class KeywordExtractor {

    public boolean isLegacy() {
        return legacy;
    }

    public void setLegacy(boolean legacy) {
        this.legacy = legacy;
    }

    private boolean legacy;

    public WordSpan[] getNameLikes(DocumentSentence sentence) {
        var direct = IntStream.range(0, sentence.length())
                .filter(i -> sentence.posTags[i].startsWith("N"))
                .mapToObj(i -> new WordSpan(i, i+1))
                ;
        var two = IntStream.range(1, sentence.length())
                .filter(i -> sentence.separators[i-1] == WordSeparator.SPACE)
                .filter(i -> isName(i, sentence, Collections.emptySet()))
                .filter(i -> isName(i -1, sentence, Collections.emptySet()))
                .mapToObj(i -> new WordSpan(i-1, i+1))
                ;

        var a_in_b = IntStream.range(2, sentence.length())
                .filter(i -> sentence.separators[i-1] == WordSeparator.SPACE)
                .filter(i -> isProperNoun(i, sentence))
                .filter(i -> isJoiner(sentence, i-1))
                .filter(i -> isProperNoun(i-2, sentence))
                .mapToObj(i -> new WordSpan(i-2, i+1))
                ;

        var a_in_det_b = IntStream.range(3, sentence.length())
                .filter(i -> sentence.separators[i-1] == WordSeparator.SPACE
                        && sentence.separators[i-2] == WordSeparator.SPACE)
                .filter(i -> isProperNoun(i, sentence))
                .filter(i -> isJoiner(sentence, i-1))
                .filter(i -> sentence.posTags[i-2].equals("DT"))
                .filter(i -> isProperNoun(i-3, sentence))
                .mapToObj(i -> new WordSpan(i-3, i+1))
                ;
        var a_in_in_b = IntStream.range(3, sentence.length())
                .filter(i -> sentence.separators[i-1] == WordSeparator.SPACE
                        && sentence.separators[i-2] == WordSeparator.SPACE)
                .filter(i -> isProperNoun(i, sentence))
                .filter(i -> isJoiner(sentence, i-1) || isProperNoun(i-1, sentence))
                .filter(i -> isJoiner(sentence, i-2) || isProperNoun(i-2, sentence))
                .filter(i -> isProperNoun(i-3, sentence))
                .mapToObj(i -> new WordSpan(i-3, i+1))
                ;
        var three = IntStream.range(2, sentence.length())
                .filter(i -> sentence.separators[i-1] == WordSeparator.SPACE
                        && sentence.separators[i-2] == WordSeparator.SPACE)
                .filter(i -> isName(i, sentence, Collections.emptySet()))
                .filter(i -> isName(i-1, sentence, Collections.emptySet()))
                .filter(i -> isName(i-2, sentence, Collections.emptySet()))
                .mapToObj(i -> new WordSpan(i-2, i+1))
                ;
        var four = IntStream.range(3, sentence.length())
                .filter(i -> sentence.separators[i-1] == WordSeparator.SPACE
                        && sentence.separators[i-2] == WordSeparator.SPACE
                        && sentence.separators[i-3] == WordSeparator.SPACE)
                .filter(i -> isName(i, sentence, Collections.emptySet()))
                .filter(i -> isName(i - 1, sentence, Collections.emptySet()))
                .filter(i -> isName(i - 2, sentence, Collections.emptySet()))
                .filter(i -> isName(i - 3, sentence, Collections.emptySet()))
                .mapToObj(i -> new WordSpan(i-3, i+1))
                ;

        return Stream.of(direct, two, a_in_b, a_in_in_b, a_in_det_b, three, four).flatMap(Function.identity())
                .toArray(WordSpan[]::new);
    }


    public WordSpan[] getNames(DocumentSentence sentence) {
        List<WordSpan> spans = new ArrayList<>(sentence.length());

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

            if (isProperNoun(i, sentence) && (isJoiner(sentence, i-1) || isProperNoun(i-1, sentence)) && isProperNoun(i-2, sentence))
                spans.add(new WordSpan(i-2, i+1));
        }

        for (int i = 3; i < sentence.length(); i++) {
            if (sentence.separators[i-3] == WordSeparator.COMMA) { continue; }
            if (sentence.separators[i-2] == WordSeparator.COMMA) { i++; continue; }
            if (sentence.separators[i-1] == WordSeparator.COMMA) { i+=2; continue; }

            if (isProperNoun(i, sentence) && isProperNoun(i-3, sentence)) {
                if (isProperNoun(i - 1, sentence) && isProperNoun(i - 2, sentence)) {
                    spans.add(new WordSpan(i-3, i+1));
                }
                else if (isJoiner(sentence, i-2) && sentence.posTags[i-1].equals("DT")) {
                    spans.add(new WordSpan(i-3, i+1));
                }
                else if ((isJoiner(sentence, i-1)||isProperNoun(i - 1, sentence)) && (isJoiner(sentence, i-2)||isProperNoun(i - 2, sentence))) {
                    spans.add(new WordSpan(i-3, i+1));
                }
            }
        }

        return spans.toArray(WordSpan[]::new);
    }

    public WordSpan[] getNamesStrict(DocumentSentence sentence) {
        List<WordSpan> spans = new ArrayList<>(sentence.length());


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
            if (isProperNoun(i, sentence) &&  (isJoiner(sentence, i-1) || isProperNoun(i-1, sentence)) && isProperNoun(i-2, sentence))
                spans.add(new WordSpan(i-2, i+1));
        }

        for (int i = 3; i < sentence.length(); i++) {
            if (sentence.separators[i-3] == WordSeparator.COMMA) { continue; }
            if (sentence.separators[i-2] == WordSeparator.COMMA) { i++; continue; }
            if (sentence.separators[i-1] == WordSeparator.COMMA) { i+=2; continue; }

            if (isProperNoun(i, sentence) && isProperNoun(i-3, sentence)) {
                if (isProperNoun(i - 1, sentence) && isProperNoun(i - 2, sentence)) {
                    spans.add(new WordSpan(i-3, i+1));
                }
                else if (isJoiner(sentence, i-1) && sentence.posTags[i-2].equals("DT")) {
                    spans.add(new WordSpan(i-3, i+1));
                }
            }
        }

        return spans.toArray(WordSpan[]::new);
    }

    public boolean isProperNoun(int i, DocumentSentence sent) {
        return "NNP".equals(sent.posTags[i]) || "NNPS".equals(sent.posTags[i]);
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

        if (word.isBlank() || WordPatterns.isStopWord(word)) return false;
        if (sentence.posTags[w.start].equals("CC")) return false;
        if (sentence.posTags[w.end-1].equals("IN")) return false;
        if (sentence.posTags[w.end-1].equals("DT")) return false;
        if (sentence.posTags[w.end-1].equals("CC")) return false;
        if (sentence.posTags[w.end-1].equals("TO")) return false;

        return true;
    }

    public WordSpan[] getKeywordsFromSentence(DocumentSentence sentence) {
        if (sentence.keywords != null) {
            return sentence.keywords.get();
        }
        List<WordSpan> spans = new ArrayList<>(sentence.length());

        Set<String> topWords = Collections.emptySet();

        for (int i = 0; i < sentence.length(); i++) {
            if (isName(i, sentence, topWords) || isTopAdj(i, sentence, topWords))
                spans.add(new WordSpan(i, i+1));
        }

        for (int i = 1; i < sentence.length(); i++) {
            if (sentence.separators[i-1] == WordSeparator.COMMA) { continue; }

            if (isName(i, sentence, topWords)) {
                if (isName(i - 1, sentence, topWords) || isTopAdj(i-1, sentence, topWords))
                    spans.add(new WordSpan(i - 1, i + 1));
            }
            if (sentence.posTags[i].equals("CD") &&  isName(i-1, sentence, topWords)) {
                spans.add(new WordSpan(i - 1, i + 1));
            }
        }

        for (int i = 2; i < sentence.length(); i++) {
            if (sentence.separators[i-1] == WordSeparator.COMMA) { i++; continue; }
            if (sentence.separators[i-2] == WordSeparator.COMMA) { continue; }

            if (isName(i, sentence, topWords)) {
                if ((isName(i-1, sentence, topWords) || isTopAdj(i-1, sentence, topWords))
                    && (isName(i-2, sentence, topWords) || isTopAdj(i-2, sentence, topWords))) {
                    spans.add(new WordSpan(i - 2, i + 1));
                }
                else if ((isProperNoun(i-1, sentence) || isJoiner(sentence, i-1)) && isProperNoun(i-2, sentence)) {
                    spans.add(new WordSpan(i - 2, i + 1));
                }
            }
            else if (sentence.posTags[i].equals("CD") && isName(i-1, sentence, topWords) && isName(i-2, sentence, topWords)) {
                spans.add(new WordSpan(i - 2, i + 1));
            }
        }

        for (int i = 3; i < sentence.length(); i++) {
            if (sentence.separators[i-1] == WordSeparator.COMMA) { i+=2; continue; }
            if (sentence.separators[i-2] == WordSeparator.COMMA) { i++; continue; }
            if (sentence.separators[i-3] == WordSeparator.COMMA) { continue; }

            if (isName(i, sentence, topWords) &&
                    (isName(i-1, sentence, topWords) || isTopAdj(i-1, sentence, topWords)) &&
                    (isName(i-2, sentence, topWords) || isTopAdj(i-2, sentence, topWords)) &&
                    (isName(i-3, sentence, topWords) || isTopAdj(i-3, sentence, topWords))) {
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

    public WordSpan[] getKeywordsFromSentenceStrict(DocumentSentence sentence, Set<String> topWords, boolean reducePartials) {
        List<WordSpan> spans = new ArrayList<>(sentence.length());

        if (!reducePartials) {
            for (int i = 0; i < sentence.length(); i++) {
                if (topWords.contains(sentence.stemmedWords[i]))
                    spans.add(new WordSpan(i, i + 1));
            }
        }

        for (int i = 1; i < sentence.length(); i++) {
            if (sentence.separators[i-1] == WordSeparator.COMMA) { continue; }

            if (topWords.contains(sentence.stemmedWords[i])
                && !sentence.words[i].endsWith("'s")
                && topWords.contains(sentence.stemmedWords[i-1])) {
                spans.add(new WordSpan(i-1, i + 1));
            }
        }
        for (int i = 2; i < sentence.length(); i++) {
            if (sentence.separators[i-2] == WordSeparator.COMMA) { continue; }
            if (sentence.separators[i-1] == WordSeparator.COMMA) { i++; continue; }

            if (topWords.contains(sentence.stemmedWords[i])
                    && !sentence.words[i].endsWith("'s")
                    && (topWords.contains(sentence.stemmedWords[i-1]) || isJoiner(sentence, i-1))
                    && topWords.contains(sentence.stemmedWords[i-2])
            ) {
                spans.add(new WordSpan(i-2, i + 1));
            }
        }

        for (int i = 3; i < sentence.length(); i++) {
            if (sentence.separators[i-3] == WordSeparator.COMMA) { continue; }
            if (sentence.separators[i-2] == WordSeparator.COMMA) { i++; continue; }
            if (sentence.separators[i-1] == WordSeparator.COMMA) { i+=2; continue; }
            if (!sentence.words[i-2].endsWith("'s")) { continue; }
            if (!sentence.words[i-3].endsWith("'s")) { continue; }

            if (topWords.contains(sentence.stemmedWords[i])
                    && !sentence.words[i].endsWith("'s") && topWords.contains(sentence.stemmedWords[i-3])) {
                if (topWords.contains(sentence.stemmedWords[i-1]) && topWords.contains(sentence.stemmedWords[i-2])) {
                    spans.add(new WordSpan(i-3, i + 1));
                }
                else if (topWords.contains(sentence.stemmedWords[i-1]) && isJoiner(sentence, i-2)) {
                    spans.add(new WordSpan(i-3, i + 1));
                }
                else if (isJoiner(sentence, i-2) && sentence.posTags[i-1].equals("DT")) {
                    spans.add(new WordSpan(i-3, i + 1));
                }
                else if (isJoiner(sentence, i-2) && isJoiner(sentence, i-1)) {
                    spans.add(new WordSpan(i-3, i + 1));
                }
            }
        }

        return spans.toArray(WordSpan[]::new);
    }

    private boolean isName(int i, DocumentSentence sentence, Set<String> topWords) {
        if (!topWords.isEmpty()) {
            String posTag = sentence.posTags[i];
            String word = sentence.stemmedWords[i];

            return ((topWords.contains(word)) && (posTag.startsWith("N") || "VBN".equals(posTag)) && !sentence.isStopWord(i));
        }


        String posTag = sentence.posTags[i];

//        if (posTag.startsWith("N") || posTag.startsWith("V") || posTag.startsWith("R") || posTag.startsWith("J"))
        return (posTag.startsWith("N") || "VBN".equals(posTag)) && !sentence.isStopWord(i);
    }

    private boolean isTopAdj(int i, DocumentSentence sentence, Set<String> topWords) {
        String posTag = sentence.posTags[i];

        return (posTag.startsWith("JJ") || posTag.startsWith("R") || posTag.startsWith("VBG"));
    }
}
