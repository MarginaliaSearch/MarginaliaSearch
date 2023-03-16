# Summary Extraction

This feature attempts to find a descriptive passage of text that summarizes
what a search result "is about". It's the text you see below a search result.

It uses several naive heuristics to try to find something that makes sense,
and there is probably room for improvement. 

There are many good techniques for doing this, but they've sadly not proved 
particularly fast. Whatever solution is used needs to be able to summarize of
order of a 100,000,000 documents with a time budget of a couple of hours.

## Central Classes

* [SummaryExtractor](src/main/java/nu/marginalia/summary/SummaryExtractor.java)
* [SummaryExtractionFilter](src/main/java/nu/marginalia/summary/SummaryExtractionFilter.java) - DOM pruning algo. 
  Doesn't always work, but when it works it's pretty good.
