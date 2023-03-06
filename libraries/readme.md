# Libraries

These are libraries that are not strongly coupled to the search engine. 

* The [array](array/) library is for memory mapping large memory-areas, which Java has
bad support for. It's designed to be able to easily replaced when *Java's Foreign Function And Memory API* is released.
* The [btree](btree/) library offers a static BTree implementation based on the array library.
* [language-processing](language-processing/) contains primitives for sentence extraction and POS-tagging.

## Micro libraries

* [easy-lsh](easy-lsh/) is a simple locality-sensitive hash for document deduplication
* [guarded-regex](guarded-regex/) makes predicated regular expressions clearer
* [big-string](big-string/) offers seamless string compression
* [random-write-funnel](random-write-funnel/) is a tool for reducing write amplification when constructing 
large files out of order.

## The rest

* [misc](misc/) is just random bits and bobs that didn't fit anywhere.