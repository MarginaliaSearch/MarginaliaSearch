package nu.marginalia.converting.processor.classifier.adblock;

import nu.marginalia.converting.model.DocumentTags;
import org.jsoup.nodes.Element;

import java.util.List;

public class GoogleAnwersSpamDetector {

    private final List<String> prefixes = List.of("What", "Why", "How", "When", "Is");

    public double testP(DocumentTags tags) {
        if (trialTag(tags, "h1")) return 1;
        if (trialTag(tags, "h2")) return 1;
        if (trialTag(tags, "h3")) return 1;

        return 0;
    }

    private boolean trialTag(DocumentTags tags, String tagName) {
        int positive = 0;
        int total = 0;

        for (Element elem : tags.allHeadingTags()) {
            if (!tagName.equals(elem.normalName()))
                continue;

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
