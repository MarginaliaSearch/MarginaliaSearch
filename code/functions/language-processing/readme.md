# Language Processing

This function gathers various tools used in language processing,
keyword extraction, and so on.

## Language Processing Tool

It also houses a tool for inspecting the output of keyword extraction,
which can be accessed by running the command below from the root of the project.
The tool becomes accessible on port 8080.

```bash
$ ./gradlew :code:functions:language-processing:run
```

## Central Classes

* [SentenceExtractor](java/nu/marginalia/language/sentence/SentenceExtractor.java) - 
Creates a [DocumentLanguageData](java/nu/marginalia/language/model/DocumentLanguageData.java) from a text, containing
its words, how they stem, POS tags, and so on. 
* [LanguageConfiguration](java/nu/marginalia/language/config/LanguageConfiguration.java) - parses langauge configuration xml files into LanguageDefinition objects
* [LanguageDefinition](java/nu/marginalia/language/model/LanguageDefinition.java) - holds all per-language cusotmizations that are fed into the language processing pipeline
* [DocumentKeywordExtractor](java/nu/marginalia/keyword/DocumentKeywordExtractor.java) - extracts keywords from documents
