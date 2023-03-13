# Language Processing

This library contains various tools used in language processing.

## Central Classes

* [SentenceExtractor](src/main/java/nu/marginalia/language/sentence/SentenceExtractor.java) - 
Creates a [DocumentLanguageData](src/main/java/nu/marginalia/language/model/DocumentLanguageData.java) from a text, containing
its words, how they stem, POS tags, and so on. 

## See Also

[features-convert/keyword-extraction](../../features-convert/keyword-extraction) uses this code to identify which keywords
are important.

[features-search/query-parser](../../features-search/query-parser) also does some language processing.