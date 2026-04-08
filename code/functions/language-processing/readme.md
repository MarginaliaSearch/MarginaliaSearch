# Language Processing

This function gathers various tools used in language processing,
keyword extraction, and so on.

## Language Configuration

The files [resources/languages-default.xml](resources/languages-default.xml) and [resources/languages-experimental.xml](resources/languages-experimental.xml) hold the laguage definitions used by the search engine,
the former is used in production and the latter in most tests that require language processing. 

The search engine excludes any languages not configured in these files, though it is relatively easy to define a stub
configuration that gets a simpler behavior out of the search engine.

The XML files are gramatically self-documenting via the DTD, but to help make sense of them, here is a cheat sheet: 

 Setting              | Values                                     | Remark
----------------------|--------------------------------------------|-------
 unicodeNormalization | minimal e-accents,german,maximal-latin | [UnicodeNormalization.java](java/nu/marginalia/language/encoding/UnicodeNormalization.java) 
 stemmer | porter, snowball + variant, none |  [Stemmer.java](java/nu/marginalia/language/stemmer/Stemmer.java)
 keywordHash | asciish, utf8 |  [KeywordHasher.java](java/nu/marginalia/language/keywords/KeywordHasher.java)
 sentenceDetector | none, opennlp |  -
 rdrTagger | (provide references) | Point to files used for POS-tagging from [rdrpostagger](https://github.com/datquocnguyen/rdrpostagger) repository
 ngrams | noun, name, subject-suffix, title, keyword | Allows the serach engine to grammatically identify important words.  Define POS tag patterns (POS tagset will vary)

If you want to learn more about POS tagging, you can learn more than you need from [https://web.stanford.edu/~jurafsky/slp3/old_oct19/8.pdf](https://web.stanford.edu/~jurafsky/slp3/old_oct19/8.pdf).

## Language Processing Tool

The repository also houses a tool for inspecting the output of keyword extraction,
which can be accessed by running the command below from the root of the project.
It reads its configuration from [resources/languages-experimental.xml](resources/languages-experimental.xml).

This is helpful in evaluting how well the language configuration works.

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
