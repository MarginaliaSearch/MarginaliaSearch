package nu.marginalia.crawl.retreival;

import nu.marginalia.crawling.body.DocumentBodyExtractor;
import nu.marginalia.crawling.body.DocumentBodyResult;
import nu.marginalia.crawling.body.HttpFetchResult;
import nu.marginalia.crawl.retreival.fetcher.warc.WarcRecorder;
import nu.marginalia.model.EdgeUrl;
import org.jsoup.Jsoup;
import org.netpreserve.jwarc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

/**
 * This class is responsible for resynchronizing the crawl frontier with a partially written
 * warc file.  This may happen if the crawl is interrupted or crashes.
 * <p>
 * This is best-effort and not guaranteed to recover all data, but it should limit
 * the amount of data that is lost and needs to be re-crawled in the event of an unexpected
 * shutdown.
 */
public class CrawlerWarcResynchronizer {
    private final DomainCrawlFrontier crawlFrontier;
    private final WarcRecorder recorder;
    private static final Logger logger = LoggerFactory.getLogger(CrawlerWarcResynchronizer.class);
    public CrawlerWarcResynchronizer(DomainCrawlFrontier crawlFrontier, WarcRecorder recorder) {
        this.crawlFrontier = crawlFrontier;
        this.recorder = recorder;
    }

    public void run(Path tempFile) {
        // First pass, enqueue links
        try (var reader = new WarcReader(tempFile)) {
            WarcXResponseReference.register(reader);
            WarcXEntityRefused.register(reader);

            for (var item : reader) {
                accept(item);
            }
        } catch (IOException e) {
            logger.info(STR."Failed read full warc file \{tempFile}", e);
        }

        // Second pass, copy records to the new warc file
        try (var reader = new WarcReader(tempFile)) {
            for (var item : reader) {
                recorder.resync(item);
            }
        } catch (IOException e) {
            logger.info(STR."Failed read full warc file \{tempFile}", e);
        }
    }

    public void accept(WarcRecord item) {
        try {
            if (item instanceof WarcResponse rsp) {
                response(rsp);
            } else if (item instanceof WarcRequest req) {
                request(req);
            } else if (item instanceof WarcXEntityRefused refused) {
                refused(refused);
            }

        }
        catch (Exception ex) {
            logger.info(STR."Failed to process warc record \{item}", ex);
        }
    }

    private void refused(WarcXEntityRefused refused) {
        // In general, we don't want to re-crawl urls that were refused,
        // but to permit circumstances to change over  time, we'll
        // allow for a small chance of re-probing these entries

        if (Math.random() > 0.1) {
            crawlFrontier.addVisited(new EdgeUrl(refused.targetURI()));
        }
    }

    private void request(WarcRequest request) {
        EdgeUrl.parse(request.target()).ifPresent(crawlFrontier::addVisited);
    }

    private void response(WarcResponse rsp) {
        var url = new EdgeUrl(rsp.targetURI());

        crawlFrontier.addVisited(url);

        try {
            var response = HttpFetchResult.importWarc(rsp);
            DocumentBodyExtractor
                    .asString(response)
                    .ifPresent((ct, body) ->
            {
                var doc = Jsoup.parse(body);
                crawlFrontier.enqueueLinksFromDocument(url, doc);
            });
        }
        catch (Exception e) {
            logger.info(STR."Failed to parse response body for \{url}", e);
        }
    }


}
