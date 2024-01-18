package nu.marginalia.crawl.retreival.revisit;

import crawlercommons.robots.SimpleRobotRules;
import nu.marginalia.crawl.retreival.CrawlDataReference;
import nu.marginalia.crawl.retreival.CrawlDelayTimer;
import nu.marginalia.crawl.retreival.CrawlerRetreiver;
import nu.marginalia.crawl.retreival.DomainCrawlFrontier;
import nu.marginalia.crawl.retreival.fetcher.ContentTags;
import nu.marginalia.crawl.retreival.fetcher.warc.WarcRecorder;
import nu.marginalia.crawling.model.CrawledDocument;
import nu.marginalia.model.EdgeUrl;
import org.jsoup.Jsoup;

/** This class encapsulates the logic for re-visiting a domain that has already been crawled.
 *  We may use information from the previous crawl to inform the next crawl, specifically the
 *  E-Tag and Last-Modified headers.
 */
public class CrawlerRevisitor {
    private final DomainCrawlFrontier crawlFrontier;
    private final CrawlerRetreiver crawlerRetreiver;
    private final WarcRecorder warcRecorder;

    public CrawlerRevisitor(DomainCrawlFrontier crawlFrontier,
                            CrawlerRetreiver crawlerRetreiver,
                            WarcRecorder warcRecorder) {
        this.crawlFrontier = crawlFrontier;
        this.crawlerRetreiver = crawlerRetreiver;
        this.warcRecorder = warcRecorder;
    }

    /** Performs a re-crawl of old documents, comparing etags and last-modified */
    public int recrawl(CrawlDataReference oldCrawlData,
                       SimpleRobotRules robotsRules,
                       CrawlDelayTimer delayTimer)
    throws InterruptedException {
        int recrawled = 0;
        int retained = 0;

        for (;;) {
            CrawledDocument doc = oldCrawlData.nextDocument();

            if (doc == null)
                break;

            // This Shouldn't Happen (TM)
            var urlMaybe = EdgeUrl.parse(doc.url);
            if (urlMaybe.isEmpty())
                continue;
            var url = urlMaybe.get();

            // If we've previously 404:d on this URL, we'll refrain from trying to fetch it again
            if (doc.httpStatus == 404) {
                crawlFrontier.addVisited(url);
                continue;
            }

            if (doc.httpStatus != 200)
                continue;

            if (!robotsRules.isAllowed(url.toString())) {
                warcRecorder.flagAsRobotsTxtError(url);
                continue;
            }
            if (!crawlFrontier.filterLink(url))
                continue;
            if (!crawlFrontier.addVisited(url))
                continue;


            if (recrawled > 5
                    && retained > 0.9 * recrawled
                    && Math.random() < 0.9)
            {
                // Since it looks like most of these documents haven't changed,
                // we'll load the documents directly; but we do this in a random
                // fashion to make sure we eventually catch changes over time
                // and ensure we discover new links

                crawlFrontier.addVisited(url);

                // Hoover up any links from the document
                if (doc.httpStatus == 200 && doc.documentBody != null) {
                    var parsedDoc = Jsoup.parse(doc.documentBody);
                    crawlFrontier.enqueueLinksFromDocument(url, parsedDoc);
                }

                // Add a WARC record so we don't repeat this
                warcRecorder.writeReferenceCopy(url,
                        doc.contentType,
                        doc.httpStatus,
                        doc.documentBody,
                        new ContentTags(doc.etagMaybe, doc.lastModifiedMaybe)
                );
            }
            else {
                // GET the document with the stored document as a reference
                // providing etag and last-modified headers, so we can recycle the
                // document if it hasn't changed without actually downloading it

                var reference = new DocumentWithReference(doc, oldCrawlData);
                var result = crawlerRetreiver.fetchWriteAndSleep(url, delayTimer, reference);

                if (reference.isSame(result)) {
                    retained++;
                }

                recrawled++;
            }
        }

        return recrawled;
    }
}
