# Lexicon

The lexicon contains a mapping for words to identifiers. 

To ease index construction, it makes calculations easier if the domain of word identifiers is dense, that is, there is no gaps between ids; if there are 100 words, they're indexed 0-99 and not 5, 23, 107, 9999, 819235 etc. The lexicon exists to create such a mapping.

This lexicon is populated from a journal. The actual word data isn't mapped, but rather a 64 bit hash. As a result of the <a href="https://en.wikipedia.org/wiki/Birthday_problem">birthday paradox</a>, colissions will be rare up until about to 2<sup>32</sup> words.


The lexicon is constructed by [processes/loading-process](../../processes/loading-process) and read when
[services-core/index-service](../../services-core/index-service) interprets queries.

## Central Classes

* [KeywordLexicon](src/main/java/nu/marginalia/lexicon/KeywordLexicon.java)
* [KeywordLexiconJournal](src/main/java/nu/marginalia/lexicon/journal/KeywordLexiconJournal.java)
* [DictionaryMap](src/main/java/nu/marginalia/dict/DictionaryMap.java) comes in two versions
* * [OnHeapDictionaryMap](src/main/java/nu/marginalia/dict/OnHeapDictionaryMap.java) - basically just a fastutil Long2IntOpenHashMap
* * [OffHeapDictionaryHashMap](src/main/java/nu/marginalia/dict/OffHeapDictionaryHashMap.java) - a heavily modified trove TLongIntHashMap that uses off heap memory
