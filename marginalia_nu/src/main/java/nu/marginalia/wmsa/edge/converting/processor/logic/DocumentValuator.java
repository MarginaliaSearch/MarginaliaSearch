package nu.marginalia.wmsa.edge.converting.processor.logic;

import crawlercommons.utils.Strings;
import nu.marginalia.util.language.processing.model.DocumentLanguageData;
import nu.marginalia.wmsa.edge.converting.model.DisqualifiedException;
import nu.marginalia.wmsa.edge.crawling.model.CrawledDocument;
import nu.marginalia.wmsa.edge.model.crawl.EdgeHtmlStandard;
import org.jsoup.nodes.Document;

import java.util.Set;

import static nu.marginalia.wmsa.edge.converting.model.DisqualifiedException.DisqualificationReason.LENGTH;

public class DocumentValuator {

    private static final Set<String> filthTable = Set.of(
            "xxx", "sex", "anal", "sexy",
            "bdsm", "fetish", "porn", "camgirls", "dildo",
            "gangbang", "buttplug", "orgasm", "vibrator",
            "cameltoe", "download", "iso", "botox", "torrent",
            "jackpot", "vegas", "casino", "coinbase", "poloniex",
            "myetherwallet", "ethereum", "binance", "bitcoin",
            "litecoin", "seo", "serp"

    );

    public double getQuality(CrawledDocument crawledDocument, EdgeHtmlStandard htmlStandard, Document parsedDocument, DocumentLanguageData dld) throws DisqualifiedException {
        double smutCoefficient = dld.streamLowerCase().filter(filthTable::contains).count();
        double scriptPenalty = getScriptPenalty(parsedDocument);

        int textBodyLength = parsedDocument.text().length();
        int rawLength = crawledDocument.documentBody.length();

        if (textBodyLength == 0) {
            throw new DisqualifiedException(LENGTH);
        }

        return Math.log(textBodyLength / (double) (1+rawLength))*htmlStandard.scale
                + htmlStandard.offset
                - scriptPenalty
                - smutCoefficient;
    }


    private int getScriptPenalty(Document parsed) {
        var scriptTags = parsed.getElementsByTag("script");
        String scriptText = scriptTags.html();
        int badScript = 0;
        if (scriptText.contains(".createElement(")) {
            badScript = 1;
        }

        double scriptPenalty = 0;
        for (var tag : scriptTags) {
            String srcAttr = tag.attr("src");
            if (srcAttr.contains("wp-content") || srcAttr.contains("wp-includes") || srcAttr.contains("jquery")) {
                scriptPenalty += 0.49;
            }
            else if (!Strings.isBlank(srcAttr)) {
                scriptPenalty += 1;
            }
        }
        return (int)(scriptPenalty + badScript + (scriptText.length())/1000.);
    }

}
