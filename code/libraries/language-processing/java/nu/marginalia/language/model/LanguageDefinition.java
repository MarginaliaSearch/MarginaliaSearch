package nu.marginalia.language.model;

import nu.marginalia.language.WordPatterns;
import nu.marginalia.language.keywords.KeywordHasher;
import nu.marginalia.language.pos.PosPattern;
import nu.marginalia.language.pos.PosPatternCategory;
import nu.marginalia.language.pos.PosTagger;
import nu.marginalia.language.stemming.Stemmer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class LanguageDefinition {
    private final String isoCode;
    private final String name;
    private final Stemmer stemmer;
    private final KeywordHasher keywordHasher;

    @Nullable
    private final PosTagger posTagger;

    private final Map<PosPatternCategory, List<PosPattern>> posPatterns;
    public LanguageDefinition(String isoCode,
                              String name,
                              Stemmer stemmer,
                              KeywordHasher keywordHasher,
                              @Nullable PosTagger posTagger,
                              Map<PosPatternCategory, List<PosPattern>> posPatterns) {
        this.isoCode = isoCode;
        this.name = name;
        this.stemmer = stemmer;
        this.keywordHasher = keywordHasher;
        this.posTagger = posTagger;
        this.posPatterns = posPatterns;
    }

    public String isoCode() {
        return isoCode;
    }

    public String displayName() {
        return name;
    }

    public Stemmer stemmer() {
        return stemmer;
    }

    @Nullable
    public PosTagger posTagger() {
        return posTagger;
    }

    public KeywordHasher keywordHasher() {
        return keywordHasher;
    }

    public long[] posTagSentence(String[] words) {
        if (posTagger == null) return new long[0];
        return posTagger.tagSentence(words);
    }

    public boolean hasPosParsing() {
        return posTagger != null;
    }

    public List<PosPattern> getPosPatterns(PosPatternCategory category) {
        return posPatterns.getOrDefault(category, List.of());
    }

    public String decodePosTagName(long tagName) {
        if (hasPosParsing())
            return posTagger.decodeTagName(tagName);
        return "";
    }

    public List<WordSpan> matchGrammarPattern(DocumentSentence sentence, PosPatternCategory category) {
        List<WordSpan> spans = new ArrayList<>(2 * sentence.length());

        for (PosPattern pattern : getPosPatterns(category)) {
            pattern.matchSentence(sentence, spans);
        }

        return spans;
    }

    public boolean matchGrammarPattern(DocumentSentence sentence, PosPatternCategory category, int pos) {
        for (var pattern : getPosPatterns(category)) {
            if (pattern.isMatch(sentence, pos))
                return true;
        }
        return false;
    }

    public boolean matchGrammarPattern(DocumentSentence sentence, PosPatternCategory category, WordSpan span) {
        for (var pattern : getPosPatterns(category)) {
            if (pattern.size() != span.size())
                continue;

            if (pattern.isMatch(sentence, span.start))
                return true;
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

        if (sentence.nextCommaPos(w.start) < w.end - 1)
            return false;

        if (!matchGrammarPattern(sentence, PosPatternCategory.TITLE, w))
            return false;

        String word = sentence.constructWordFromSpan(w);
        return !word.isBlank() && WordPatterns.isNotJunkWord(word);
    }


}
