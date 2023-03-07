package nu.marginalia.converting.processor.logic.pubdate.heuristic;

import nu.marginalia.model.crawl.EdgeHtmlStandard;
import nu.marginalia.model.crawl.PubDate;
import nu.marginalia.converting.processor.logic.pubdate.PubDateHeuristic;
import nu.marginalia.converting.processor.logic.pubdate.PubDateParser;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.converting.processor.logic.pubdate.PubDateEffortLevel;
import org.jsoup.nodes.Document;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Pattern;

public class PubDateHeuristicUrlPatternPass1 implements PubDateHeuristic {

    private static final Pattern yearUrlPattern = Pattern.compile("/\\d{4}/");

    // False positive rate is much higher in the 1990s, only include 2000s+ in pass 1
    private static final int MIN_URL_PATTERN_YEAR = 2000;

    @Override
    public Optional<PubDate> apply(PubDateEffortLevel effortLevel, String headers, EdgeUrl url, Document document, EdgeHtmlStandard htmlStandard) {
        final String urlString = url.path;

        var matcher = yearUrlPattern.matcher(urlString);

        for (int i = 0; i < urlString.length() && matcher.find(i); i = matcher.end()) {

            String segment = urlString.substring(matcher.start() + 1, matcher.end() - 1);

            OptionalInt year = PubDateParser.parseYearString(segment);

            if (year.isEmpty())
                continue;

            int y = year.getAsInt();
            if (y >= MIN_URL_PATTERN_YEAR && y <= PubDate.MAX_YEAR) {
                return Optional.of(new PubDate(null, y));
            }
        }

        return Optional.empty();
    }
}
