package nu.marginalia.crawl.retreival.revisit;

import com.google.common.base.Strings;
import crawlercommons.robots.SimpleRobotRules;
import nu.marginalia.crawl.retreival.CrawlDataReference;
import nu.marginalia.crawl.retreival.CrawlDelayTimer;
import nu.marginalia.crawl.retreival.CrawlerRetreiver;
import nu.marginalia.crawl.retreival.DomainCrawlFrontier;
import nu.marginalia.crawl.retreival.fetcher.ContentTags;
import nu.marginalia.crawl.retreival.fetcher.warc.WarcRecorder;
import nu.marginalia.crawling.body.HttpFetchResult;
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
        int errors = 0;

        for (;;) {
            if (errors > 20) {
                // If we've had too many errors, we'll stop trying to recrawl
                break;
            }

            CrawledDocument doc = oldCrawlData.nextDocument();

            if (doc == null)
                break;

            // This Shouldn't Happen (TM)
            var urlMaybe = EdgeUrl.parse(doc.url);
            if (urlMaybe.isEmpty())
                continue;
            var url = urlMaybe.get();

            // If we've previously 404:d on this URL, we'll refrain from trying to fetch it again,
            // since it's likely to 404 again.  It will be forgotten by the next crawl though, so
            // we'll eventually try again.

            if (doc.httpStatus == 404) {
                crawlFrontier.addVisited(url);
                continue;
            }

            // If the reference document is empty or the HTTP status is not 200, we'll skip it since it's
            // unlikely to produce anything meaningful for us.
            if (doc.httpStatus != 200)
                continue;
            if (Strings.isNullOrEmpty(doc.documentBody))
                continue;

            if (!crawlFrontier.filterLink(url))
                continue;

            if (!crawlFrontier.addVisited(url))
                continue;

            if (!robotsRules.isAllowed(url.toString())) {
                warcRecorder.flagAsRobotsTxtError(url);
                continue;
            }


            if (recrawled > 5
                    && retained > 0.9 * recrawled
                    && Math.random() < 0.9)
            {
                // Since it looks like most of these documents haven't changed,
                // we'll load the documents directly; but we do this in a random
                // fashion to make sure we eventually catch changes over time
                // and ensure we discover new links

                // Hoover up any links from the document
                crawlFrontier.enqueueLinksFromDocument(url, Jsoup.parse(doc.documentBody));

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

                DocumentWithReference reference =  new DocumentWithReference(doc, oldCrawlData);

                var result = crawlerRetreiver.fetchWriteAndSleep(url, delayTimer, reference);

                if (reference.isSame(result)) {
                    retained++;
                }
                else if (result instanceof HttpFetchResult.ResultException) {
                    errors++;
                }

                recrawled++;
            }
        }

        return recrawled;
    }
}
