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

## Configuration

The crawler supports various configuration options via system properties that can be set in `system.properties`:

### Crawler Behavior
- `crawler.crawlSetGrowthFactor` (default: 1.25) - Growth factor for crawl depth
- `crawler.minUrlsPerDomain` (default: 100) - Minimum URLs to crawl per domain
- `crawler.maxUrlsPerDomain` (default: 10000) - Maximum URLs to crawl per domain
- `crawler.poolSize` (default: 256) - Thread pool size for concurrent crawling
- `crawler.useVirtualThreads` (default: false) - Use virtual threads instead of platform threads
- `crawler.maxConcurrentRequests` (default: 512) - Maximum concurrent HTTP requests
- `crawler.maxFetchSize` (default: 33554432) - Maximum fetch size in bytes

### Timeout Configuration
- `crawler.socketTimeout` (default: 10) - Socket timeout in seconds
- `crawler.connectTimeout` (default: 30) - Connection timeout in seconds
- `crawler.responseTimeout` (default: 10) - Response timeout in seconds
- `crawler.connectionRequestTimeout` (default: 5) - Connection request timeout in minutes
- `crawler.jvmConnectTimeout` (default: 30000) - JVM-level connect timeout in milliseconds
- `crawler.jvmReadTimeout` (default: 30000) - JVM-level read timeout in milliseconds
- `crawler.httpClientIdleTimeout` (default: 15) - HTTP client idle timeout in seconds
- `crawler.httpClientConnectionPoolSize` (default: 256) - HTTP client connection pool size

### User Agent Configuration
- `crawler.userAgentString` - Custom user agent string
- `crawler.userAgentIdentifier` - User agent identifier

### Other Options
- `links.block_mailing_lists` (default: false) - Block mailing list links
- `ip-blocklist.disabled` (default: false) - Disable IP blocklist

## Central Classes

* [CrawlerMain](java/nu/marginalia/crawl/CrawlerMain.java) orchestrates the crawling.
* [CrawlerRetreiver](java/nu/marginalia/crawl/retreival/CrawlerRetreiver.java)
  visits known addresses from a domain and downloads each document.
* [HttpFetcher](java/nu/marginalia/crawl/retreival/fetcher/HttpFetcherImpl.java)
  fetches URLs.