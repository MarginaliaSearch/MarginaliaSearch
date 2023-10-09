# Crawling Process

The crawling process downloads HTML and saves them
into per-domain snapshots.

## Central Classes

* [CrawlerMain](src/main/java/nu/marginalia/crawl/CrawlerMain.java) orchestrates the crawling.
* [CrawlerRetreiver](src/main/java/nu/marginalia/crawl/retreival/CrawlerRetreiver.java)
  visits known addresses from a domain and downloads each document.
* [HttpFetcher](src/main/java/nu/marginalia/crawl/retreival/fetcher/HttpFetcherImpl.java)
  fetches a URL.

## See Also

* [features-convert](../../features-convert/)