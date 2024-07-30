package nu.marginalia.converting.processor.classifier.adblock;

import org.jsoup.nodes.Document;

import java.util.List;

public class GoogleAnwersSpamDetector {

    private final List<String> prefixes = List.of("What", "Why", "How", "When", "Is");

    public double testP(Document doc) {
        if (trialTag(doc, "h1")) return 1;
        if (trialTag(doc, "h2")) return 1;
        if (trialTag(doc, "h3")) return 1;

        return 0;
    }

    private boolean trialTag(Document doc, String tagName) {
        int positive = 0;
        int total = 0;

        for (var elem : doc.getElementsByTag(tagName)) {
            String text = elem.text();
            for (var prefix : prefixes) {
                if (text.startsWith(prefix)) {
                    positive++;
                    break;
                }
            }
            total ++;
        }

        return positive > 4 && positive / (double) total > 0.5;
    }
}
