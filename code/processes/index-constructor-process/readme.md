The index construction process is responsible for creating the indexes
used by the search engine.  

There are three types of indexes:

* The forward index, which maps documents to words.
* The full reverse index, which maps words to documents; and includes all words.
* The priority reverse index, which maps words to documents; but includes only the most "important" words (such as 
  those appearing in the title, or with especially high TF-IDF scores).

This is a very light-weight module that delegates the actual work to the modules:

* [features-index/index-reverse](../../index/index-reverse)
* [features-index/index-forward](../../index/index-forward) 

Their respective readme files contain more information about the indexes themselves
and how they are constructed.

The process is glued together within [IndexConstructorMain](java/nu/marginalia/index/IndexConstructorMain.java),
which is the only class of interest in this module. 
