# Crawling Process

The crawling process downloads HTML and saves them into per-domain snapshots.  The crawler seeks out HTML documents,
and ignores other types of documents, such as PDFs.  Crawling is done on a domain-by-domain basis, and the crawler
does not follow links to other domains within a single job.

The crawler stores data from crawls in-progress in a WARC file.  Once the crawl is complete, the WARC file is
converted to a parquet file, which is then used by the [converting process](../converting-process/).  The intermediate
WARC file is not used by any other process, but kept to be able to recover the state of a crawl in case of a crash or
other failure.

If configured so, these crawls may be retained.  This is not the default behavior, as the WARC format is not very dense,
and the parquet files are much more efficient.  However, the WARC files are useful for debugging and integration with
other tools.

## Robots Rules

A significant part of the crawler is dealing with `robots.txt` and similar, rate limiting headers; especially when these
are not served in a standard way (which is very common).  [RFC9390](https://www.rfc-editor.org/rfc/rfc9309.html) as well as Google's [Robots.txt Specifications](https://developers.google.com/search/docs/advanced/robots/robots_txt) are good references.

## Re-crawling

The crawler can use old crawl data to avoid re-downloading documents that have not changed.  This is done by
comparing the old and new documents using the HTTP `If-Modified-Since` and `If-None-Match` headers.  If a large
proportion of the documents have not changed, the crawler falls into a mode where it only randomly samples a few
documents from each domain, to avoid wasting time and resources on domains that have not changed.

## Sitemaps and rss-feeds

On top of organic links, the crawler can use sitemaps and rss-feeds to discover new documents.

## Central Classes

* [CrawlerMain](src/main/java/nu/marginalia/crawl/CrawlerMain.java) orchestrates the crawling.
* [CrawlerRetreiver](src/main/java/nu/marginalia/crawl/retreival/CrawlerRetreiver.java)
  visits known addresses from a domain and downloads each document.
* [HttpFetcher](src/main/java/nu/marginalia/crawl/retreival/fetcher/HttpFetcherImpl.java)
  fetches URLs.

## See Also

* [features-crawl](../../features-crawl/)