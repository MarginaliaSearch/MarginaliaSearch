Client for [FeedlotTheFeedBot](https://github.com/MarginaliaSearch/FeedLotTheFeedBot),
the RSS/Atom feed fetcher and cache for Marginalia Search.

This service is external to the Marginalia Search codebase,
as it is not a core part of the search engine and has other
utilities.

## Example

```java

import java.time.Duration;

var client = new FeedlotClient("localhost", 8080, 
        gson, 
        Duration.ofMillis(100),  // connect timeout
        Duration.ofMillis(100)); // request timeout

CompleteableFuture<FeedItems> items = client.getFeedItems("www.marginalia.nu");
```