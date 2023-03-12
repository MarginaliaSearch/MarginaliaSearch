# Index Journal

The index journal contains a list of entries with keywords and keyword metadata per document. 

This journal is written by [crawl-processes/loading-process](../../crawl-processes/loading-process) and read 
when constructing the [forward](../index-forward) and [reverse](../index-reverse) 
indices. 

## Central Classes

### Model
* [IndexJournalEntry](src/main/java/nu.marginalia.index/journal/model/IndexJournalEntry.java)
* [IndexJournalEntryHeader](src/main/java/nu.marginalia.index/journal/model/IndexJournalEntryHeader.java)
* [IndexJournalEntryData](src/main/java/nu.marginalia.index/journal/model/IndexJournalEntryData.java)
### I/O
* [IndexJournalReader](src/main/java/nu.marginalia.index/journal/reader/IndexJournalReader.java)
* [IndexJournalWriter](src/main/java/nu.marginalia.index/journal/writer/IndexJournalWriter.java)