# Converting Process

The converting process reads crawl data and extracts information to be fed into the index,
such as keywords, metadata, urls, descriptions...

## Central Classes

* [ConverterMain](src/main/java/nu/marginalia/converting/ConverterMain.java) orchestrates the conversion process.
* [DocumentProcessor](src/main/java/nu/marginalia/converting/processor/DocumentProcessor.java) converts a single document.
* - [HtmlDocumentProcessorPlugin](src/main/java/nu/marginalia/converting/processor/plugin/HtmlDocumentProcessorPlugin.java) 
has HTML-specific logic related to a document, keywords and identifies features such as whether it has javascript.
* - [PlainTextDocumentProcessorPlugin](src/main/java/nu/marginalia/converting/processor/plugin/PlainTextDocumentProcessorPlugin.java)
  has plain text-specific logic related to a document...
* [DomainProcessor](src/main/java/nu/marginalia/converting/processor/DomainProcessor.java) converts each document and 
generates domain-wide metadata such as link graphs.
