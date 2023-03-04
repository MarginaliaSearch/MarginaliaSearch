package nu.marginalia.converting.processor;

import nu.marginalia.crawling.model.CrawledDocument;
import org.jsoup.nodes.Document;


public class AcceptableAds {
    /* Acceptable Ads is an initiative to allow less intrusive ads to punch through adblockers.
     *
     * In practice, from looking at crawled data, the only sites in the crawled corpus that seem to
     * follow this standard are domain squatters and other nuisance sites.
     *
     */

    public static boolean hasAcceptableAdsTag(Document parsedDocument) {
        return parsedDocument.getElementsByTag("html").hasAttr("data-adblockkey");
    }

    public static boolean hasAcceptableAdsHeader(CrawledDocument document) {
        return document.headers.contains("X-Adblock-Key");
    }
}
