package nu.marginalia.converting.processor.summary;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import nu.marginalia.converting.processor.summary.heuristic.*;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SummaryExtractor {
    private final int maxSummaryLength;

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
                return abbreivateSummary(maybe);
            }
        }
        return "";
    }

    public String abbreivateSummary(String summary) {
        return StringUtils.abbreviate(summary, "", maxSummaryLength);
    }
}
