# Index Journal

The index journal contains a list of entries with keywords and keyword metadata per document. 

This journal is written by [processes/loading-process](../../processes/loading-process) and read 
when constructing the [forward](../index-forward) and [reverse](../index-reverse) 
indices. 

The journal uses the [Slop library](https://github.com/MarginaliaSearch/SlopData) to store data
in a columnar fashion. 

The journal will may be split into multiple files to help index
construction, as a merge strategy is used to reduce the amount
of RAM required during index construction.

Unlike most slop data stores, the index journal allows direct access
to the underlying columns, as the needs of the index construction processes
are fairly varied. 