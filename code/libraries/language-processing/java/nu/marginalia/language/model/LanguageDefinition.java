package nu.marginalia.language.model;

import nu.marginalia.language.keywords.KeywordHasher;
import nu.marginalia.language.pos.PosPattern;
import nu.marginalia.language.pos.PosPatternCategory;
import nu.marginalia.language.pos.PosTagger;
import nu.marginalia.language.stemming.Stemmer;

import java.util.List;
import java.util.Map;

public record LanguageDefinition(String isoCode,
                                 String name,
                                 Stemmer stemmer,
                                 KeywordHasher keywordHasher,
                                 PosTagger posTagger,
                                 Map<PosPatternCategory, List<PosPattern>> posPatterns)
{

    public List<PosPattern> getPatterns(PosPatternCategory category) {
        return posPatterns.getOrDefault(category, List.of());
    }
}
