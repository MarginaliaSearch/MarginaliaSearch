package nu.marginalia.language.model;

import nu.marginalia.language.keywords.KeywordHasher;
import nu.marginalia.language.pos.PosPattern;
import nu.marginalia.language.pos.PosPatternCategory;
import nu.marginalia.language.pos.PosTagger;
import nu.marginalia.language.stemming.Stemmer;

import javax.annotation.Nullable;
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


}
