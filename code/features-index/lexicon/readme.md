# Lexicon

The lexicon contains a mapping for words to identifiers. This lexicon is populated from a journal.
The actual word data isn't mapped, but rather a 64 bit hash. 

The lexicon is constructed by [processes/loading-process](../../processes/loading-process) and read when
[services-core/index-service](../../services-core/index-service) interprets queries.

## Central Classes

* [KeywordLexicon](src/main/java/nu/marginalia/lexicon/KeywordLexicon.java)
* [KeywordLexiconJournal](src/main/java/nu/marginalia/lexicon/journal/KeywordLexiconJournal.java)
* [DictionaryMap](src/main/java/nu/marginalia/dict/DictionaryMap.java) comes in two versions
* * [OnHeapDictionaryMap](src/main/java/nu/marginalia/dict/OnHeapDictionaryMap.java) - basically just a fastutil Long2IntOpenHashMap
* * [OffHeapDictionaryHashMap](src/main/java/nu/marginalia/dict/OffHeapDictionaryHashMap.java) - a heavily modified trove TLongIntHashMap that uses off heap memory
