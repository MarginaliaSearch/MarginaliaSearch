package nu.marginalia.language.model;

import nu.marginalia.language.stemming.Stemmer;

public record LanguageDefinition(String isoCode,
                                 String name,
                                 Stemmer stemmer)
{

}
