package nu.marginalia.converting.processor.logic.summary;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.regex.Pattern;

public class SummaryExtractor {
    private final int maxSummaryLength;

    private final Pattern truncatedCharacters = Pattern.compile("[\\-.,!?' ]{3,}");

    @Inject
    public SummaryExtractor(@Named("max-summary-length") Integer maxSummaryLength) {
        this.maxSummaryLength = maxSummaryLength;
    }

    public String extractSummary(Document parsed) {
        String summaryString;

        summaryString = extractSummaryRaw(parsed);
        summaryString = truncatedCharacters.matcher(summaryString).replaceAll(" ");
        summaryString = StringUtils.abbreviate(summaryString, "", maxSummaryLength);

        return summaryString;
    }


    private String extractSummaryRaw(Document parsed) {

        String maybe;

        parsed.select("header,nav,#header,#nav,#navigation,.header,.nav,.navigation,ul,li").remove();

        // Plan A
        maybe = getSummaryNew(parsed.clone());
        if (!maybe.isBlank()) return maybe;

        maybe = getSummaryByTagDensity(parsed.clone());
        if (!maybe.isBlank()) return maybe;

        // Plan B: Open Graph Description
        maybe = parsed.select("meta[name=og:description]").attr("content");
        if (!maybe.isBlank()) return maybe;

        // Plan C: Ye Olde meta-description
        maybe = parsed.select("meta[name=description]").attr("content");
        if (!maybe.isBlank()) return maybe;

        // Plan D: The kitchen sink?
        return lastDitchSummaryEffort(parsed);
    }

    private String getSummaryNew(Document parsed) {
        var filter = new SummaryExtractionFilter();

        parsed.filter(filter);

        return filter.getSummary(maxSummaryLength+32);
    }

    private String getSummaryByTagDensity(Document parsed) {
        StringBuilder content = new StringBuilder();

        for (var elem : parsed.select("p,div,section,article,font,center")) {
            if (content.length() >= maxSummaryLength) break;

            String tagName = elem.tagName();
            if (("p".equals(tagName) || "center".equals(tagName) || "font".equals(tagName))
                    && elem.text().length() < 16)
            {
                continue;
            }

            if (aTagDensity(elem) < 0.1 && htmlTagDensity(elem) > 0.85) {
                content.append(elem.text()).append(' ');
            }
        }

        if (content.length() > 32) {
            // AAAA AAAA AAAA AAAA AAAA AAAA AAAA AAAA
            return content.toString();
        }

        return "";
    }

    private String lastDitchSummaryEffort(Document parsed) {
        int bodyTextLength = parsed.body().text().length();

        parsed.getElementsByTag("a").remove();

        for (var elem : parsed.select("p,div,section,article,font,center,td,h1,h2,h3,h4,h5,h6,tr,th")) {
            if (elem.text().length() < bodyTextLength / 2 && aTagDensity(elem) > 0.25) {
                elem.remove();
            }
        }

        return parsed.body().text();
    }

    private double htmlTagDensity(Element elem) {
        return (double) elem.text().length() / elem.html().length();
    }

    private double aTagDensity(Element elem) {
        return (double) elem.getElementsByTag("a").text().length() / elem.text().length();
    }

}
