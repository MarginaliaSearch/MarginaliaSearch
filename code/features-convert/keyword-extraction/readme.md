# Keyword Extraction

This code deals with identifying keywords in a document, their positions in the document,
their important based on [TF-IDF](https://en.wikipedia.org/wiki/Tf-idf) and their grammatical 
functions based on [POS tags](https://www.ling.upenn.edu/courses/Fall_2003/ling001/penn_treebank_pos.html).

## Central Classes

* [DocumentKeywordExtractor](src/main/java/nu/marginalia/keyword/DocumentKeywordExtractor.java)
* [KeywordMetadata](src/main/java/nu/marginalia/keyword/KeywordMetadata.java)

## See Also

* [libraries/language-processing](../../libraries/language-processing) does a lot of the heavy lifting.