package nu.marginalia.wmsa.edge.converting.processor.logic;

import crawlercommons.utils.Strings;
import nu.marginalia.wmsa.edge.converting.model.DisqualifiedException;
import nu.marginalia.wmsa.edge.crawler.domain.language.processing.model.DocumentLanguageData;
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

    public double getQuality(EdgeHtmlStandard htmlStandard, Document doc, DocumentLanguageData dld) throws DisqualifiedException {
        double smutCoefficient = dld.streamLowerCase().filter(filthTable::contains).count();
        double scriptPenalty = getScriptPenalty(doc);


        int textBodyLength = doc.text().length();
        int rawLength = doc.html().length();

        if (textBodyLength == 0) {
            throw new DisqualifiedException(LENGTH);
        }

        return Math.log(textBodyLength / (double) rawLength)*htmlStandard.scale
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
            String srcTag = tag.attr("src");
            if (Strings.isBlank(srcTag)) {
                scriptPenalty += 1;
            }
            else if (srcTag.contains("wp-content") || srcTag.contains("wp-includes") || srcTag.contains("jquery")) {
                scriptPenalty += 0.49;
            }
            else {
                scriptPenalty += 1;
            }

        }
        return (int)(scriptPenalty + badScript + (scriptText.length())/1000.);
    }

}
