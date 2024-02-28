# Index Journal

The index journal contains a list of entries with keywords and keyword metadata per document. 

This journal is written by [processes/loading-process](../../processes/loading-process) and read 
when constructing the [forward](../index-forward) and [reverse](../index-reverse) 
indices. 

The journal format is a file header, followed by a zstd-compressed list of entries,
each containing a header with document-level data, and a data section
with keyword-level data.

The journal data may be split into multiple files, and the journal writers and readers
are designed to handle this transparently via their *Paging* implementation.

## Central Classes

### Model
* [IndexJournalEntry](java/nu/marginalia/index/journal/model/IndexJournalEntry.java)
* [IndexJournalEntryHeader](java/nu/marginalia/index/journal/model/IndexJournalEntryHeader.java)
* [IndexJournalEntryData](java/nu/marginalia/index/journal/model/IndexJournalEntryData.java)
### I/O
* [IndexJournalReader](java/nu/marginalia/index/journal/reader/IndexJournalReader.java)
* [IndexJournalWriter](java/nu/marginalia/index/journal/writer/IndexJournalWriter.java)