package nu.marginalia.pubdate.heuristic;

import nu.marginalia.crawling.common.model.HtmlStandard;
import nu.marginalia.model.crawl.PubDate;
import nu.marginalia.pubdate.PubDateHeuristic;
import nu.marginalia.pubdate.PubDateParser;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.pubdate.PubDateEffortLevel;
import org.jsoup.nodes.Document;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Pattern;

public class PubDateHeuristicUrlPatternPass2 implements PubDateHeuristic {

    private static final Pattern yearUrlPattern = Pattern.compile("/\\d{4}/");

    @Override
    public Optional<PubDate> apply(PubDateEffortLevel effortLevel, String headers, EdgeUrl url, Document document, HtmlStandard htmlStandard) {
        final String urlString = url.path;

        var matcher = yearUrlPattern.matcher(urlString);

        for (int i = 0; i < urlString.length() && matcher.find(i); i = matcher.end()) {

            String segment = urlString.substring(matcher.start() + 1, matcher.end() - 1);

            OptionalInt year = PubDateParser.parseYearString(segment);

            if (year.isEmpty())
                continue;

            int y = year.getAsInt();
            if (y >= PubDate.MIN_YEAR && y <= PubDate.MAX_YEAR) {
                return Optional.of(new PubDate(null, y));
            }
        }

        return Optional.empty();
    }
}
