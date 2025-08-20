package nu.marginalia.language.model;

import nu.marginalia.language.keywords.KeywordHasher;
import nu.marginalia.language.pos.PosTaggingData;
import nu.marginalia.language.stemming.Stemmer;

public record LanguageDefinition(String isoCode,
                                 String name,
                                 Stemmer stemmer,
                                 KeywordHasher keywordHasher,
                                 PosTaggingData posTaggingData)
{

}
