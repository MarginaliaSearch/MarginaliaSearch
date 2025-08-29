package nu.marginalia.keyword;

import nu.marginalia.language.WordPatterns;
import nu.marginalia.language.model.DocumentSentence;
import nu.marginalia.language.model.LanguageDefinition;
import nu.marginalia.language.model.WordSpan;
import nu.marginalia.language.pos.PosPattern;
import nu.marginalia.language.pos.PosPatternCategory;

import java.util.ArrayList;
import java.util.List;

public class KeywordExtractor {
    private final LanguageDefinition languageDefinition;

    public KeywordExtractor(LanguageDefinition languageDefinition) {
        this.languageDefinition = languageDefinition;
    }

    public List<WordSpan> matchGrammarPattern(DocumentSentence sentence, PosPatternCategory category) {
        List<WordSpan> spans = new ArrayList<>(2 * sentence.length());

        for (PosPattern pattern : languageDefinition.getPosPatterns(category)) {
            pattern.matchSentence(sentence, spans);
        }

        return spans;
    }

    public boolean matchGrammarPattern(DocumentSentence sentence, PosPatternCategory category, int pos) {
        for (var pattern : languageDefinition.getPosPatterns(category)) {
            if (pattern.isMatch(sentence, pos))
                return true;
        }
        return false;
    }

    public boolean matchGrammarPattern(DocumentSentence sentence, PosPatternCategory category, WordSpan span) {
        for (var pattern : languageDefinition.getPosPatterns(category)) {
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
