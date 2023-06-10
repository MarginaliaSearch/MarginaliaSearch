package nu.marginalia.summary;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import nu.marginalia.summary.heuristic.*;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

public class SummaryExtractor {
    private final int maxSummaryLength;

    private final Pattern truncatedCharacters = Pattern.compile("[\\-.,!?' ]{3,}");

    private final List<SummaryHeuristic> heuristics = new ArrayList<>();

    @Inject
    public SummaryExtractor(@Named("max-summary-length") Integer maxSummaryLength,
                            DomFilterHeuristic domFilterHeuristic,
                            TagDensityHeuristic tagDensityHeuristic,
                            OpenGraphDescriptionHeuristic ogTagHeuristic,
                            MetaDescriptionHeuristic metaDescriptionHeuristic,
                            FallbackHeuristic fallbackHeuristic)
    {
        this.maxSummaryLength = maxSummaryLength;

        heuristics.add(domFilterHeuristic);
        heuristics.add(tagDensityHeuristic);
        heuristics.add(ogTagHeuristic);
        heuristics.add(metaDescriptionHeuristic);
        heuristics.add(fallbackHeuristic);
    }

    public String extractSummary(Document parsed, Collection<String> importantWords) {
        parsed.select("header,nav,#header,#nav,#navigation,.header,.nav,.navigation,ul,li").remove();

        for (var heuristic : heuristics) {
            String maybe = heuristic.summarize(parsed, importantWords);
            if (!maybe.isBlank()) {
                String cleaned = truncatedCharacters.matcher(maybe).replaceAll(" ");
                return StringUtils.abbreviate(cleaned, "", maxSummaryLength);
            }
        }
        return "";
    }

}
