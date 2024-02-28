# Converting Process

The converting process reads crawl data and extracts information to be fed into the index,
such as keywords, metadata, urls, descriptions...

The converter reads crawl data in the form of parquet files, and writes the extracted data to parquet 
files on a different format.  These files are then passed to the loader process, which does additional 
processing needed to feed the data into the index.

The reason for splitting the process into two parts is that the heavier converting process can be terminated
and restarted without losing progress, while the lighter loader process needs to be run in a single
go (or restarted if it crashes/terminates).

The converter output is also in general more portable and can be used for different tasks, meanwhile the
loader's output is heavily tailored to the index and not much use for anything else.

## Structure

Most information is extracted from the document itself within `DocumentProcessor`, but some information is extracted from the
context of the document, such as other documents on the same domain.  This is done in `DomainProcessor`.

To support multiple document formats, the converting process is pluggable. Each plugin is responsible for
converting a single document format, such as HTML or plain text.  

Further, the HTML plugin supports specializations, which refine the conversion process for specific 
server software, such as Javadoc, MediaWiki, PhpBB, etc.  This helps to improve the processing for 
common types of websites, and makes up for the fact that it's hard to build a one-size-fits-all heuristic
for deciding which parts of a document are important that does justice to every website.

## Anchor Text

The converting process also supports supplementing the data with external information, such as anchor texts. 
This is done automatically if `atags.parquet` is available in the `data/`-directory.  atags.parquet can be
downloaded from [here](https://downloads.marginalia.nu/exports/). 

The rationale for doing this as well as the details of how the file is generated is described in this blog post: 
https://www.marginalia.nu/log/93_atags/ 

## Central Classes

* [ConverterMain](java/nu/marginalia/converting/ConverterMain.java) orchestrates the conversion process.
* [DocumentProcessor](java/nu/marginalia/converting/processor/DocumentProcessor.java) converts a single document.
* - [HtmlDocumentProcessorPlugin](java/nu/marginalia/converting/processor/plugin/HtmlDocumentProcessorPlugin.java) 
has HTML-specific logic related to a document, keywords and identifies features such as whether it has javascript.
* * - [HtmlProcessorSpecializations](java/nu/marginalia/converting/processor/plugin/specialization/HtmlProcessorSpecializations.java)
* * - [XenForoSpecialization](java/nu/marginalia/converting/processor/plugin/specialization/XenForoSpecialization.java) ...
* - [PlainTextDocumentProcessorPlugin](java/nu/marginalia/converting/processor/plugin/PlainTextDocumentProcessorPlugin.java)
  has plain text-specific logic related to a document...

* [DomainProcessor](java/nu/marginalia/converting/processor/DomainProcessor.java) converts each document and 
generates domain-wide metadata such as link graphs.

## See Also

* [features-convert](../../features-convert/)