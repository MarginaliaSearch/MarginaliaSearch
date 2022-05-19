package nu.marginalia.wmsa.edge.converting.processor.logic;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.Optional;
import java.util.regex.Pattern;

public class SummaryExtractor {
    private final int maxSummaryLength;

    private final Pattern truncatedCharacters = Pattern.compile("[^a-zA-Z0-9.,!?\\-'\"]+");

    @Inject
    public SummaryExtractor(@Named("max-summary-length") Integer maxSummaryLength) {
        this.maxSummaryLength = maxSummaryLength;
    }

    public Optional<String> extractSummary(Document parsed) {
        var cleanDoc = parsed.clone();
        cleanDoc.select("h1,h2,h3,header,nav,#header,#nav,#navigation,.header,.nav,.navigation,ul,li").remove();

        return extractSummaryRaw(cleanDoc)
                .map(String::trim)
                .filter(s -> !s.isBlank() && s.length() > 20)
                .or(() -> getOgDescription(parsed))
                .or(() -> getMetaDescription(parsed))
                .map(this::trimLongSpaces)
                .map(s -> StringUtils.abbreviate(s, "", maxSummaryLength))
                ;
    }

    private String trimLongSpaces(String s) {
        return truncatedCharacters.matcher(s).replaceAll(" ");
    }

    private Optional<String> extractSummaryRaw(Document parsed) {
        StringBuilder content = new StringBuilder();

        parsed.select("p,div,section,article").stream()
                .takeWhile(e -> content.length() <= maxSummaryLength)
                .filter(elem -> elem.text().length() > elem.html().length()/2)
                .map(Element::text)
                .forEach(content::append);

        if (content.length() > 10) {
            return Optional.of(content.toString());
        }
        return Optional.empty();
    }

    private Optional<String> getMetaDescription(Document parsed) {
        return Optional.of(parsed.select("meta[name=description]").attr("content")).filter(s -> !s.isBlank());
    }

    private Optional<String> getOgDescription(Document parsed) {
        return Optional.of(parsed.select("meta[name=og:description]").attr("content")).filter(s -> !s.isBlank());
    }
}
