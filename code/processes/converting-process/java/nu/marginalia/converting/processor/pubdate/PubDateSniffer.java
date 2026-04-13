package nu.marginalia.converting.processor.pubdate;

import nu.marginalia.converting.model.DocumentHeaders;
import nu.marginalia.converting.processor.pubdate.heuristic.*;
import nu.marginalia.model.DocumentFormat;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawl.PubDate;
import org.jsoup.nodes.Document;

import java.util.ArrayList;
import java.util.List;

public class PubDateSniffer {

    private final List<PubDateHeuristic> highQualityHeuristics = new ArrayList<>();
    private final List<PubDateHeuristic> lowQualityHeuristics = new ArrayList<>();

    public PubDateSniffer() {
        highQualityHeuristics.add(new PubDateHeuristicJSONLD());
        highQualityHeuristics.add(new PubDateHeuristicMicrodata());
        highQualityHeuristics.add(new PubDateHeuristicOpenGraph());
        highQualityHeuristics.add(new PubDateHeuristicRDFaTag());

        highQualityHeuristics.add(new PubDateHeuristicHtml5ItempropDateTag());
        highQualityHeuristics.add(new PubDateHeuristicHtml5ArticleDateTag());

        lowQualityHeuristics.add(new PubDateHeuristicUrlPatternPass1());

        lowQualityHeuristics.add(new PubDateHeuristicDOMParsingPass1());
        lowQualityHeuristics.add(new PubDateHeuristicHtml5AnyTimeTag());

        lowQualityHeuristics.add(new PubDateHeuristicDOMParsingPass2());
        lowQualityHeuristics.add(new PubDateHeuristicUrlPatternPass2());

        lowQualityHeuristics.add(new PubDateHeuristicLastModified());

        lowQualityHeuristics.add(new PubDateHeuristicGuessFromHtmlStandard());
    }

    public PubDate getPubDate(DocumentHeaders headers, EdgeUrl url, Document document, DocumentFormat htmlStandard, boolean runExpensive) {
        final PubDateEffortLevel effortLevel = runExpensive ? PubDateEffortLevel.HIGH : PubDateEffortLevel.LOW;

        for (var heuristic : highQualityHeuristics) {
            var maybe = heuristic.apply(effortLevel, headers, url, document, htmlStandard);
            if (maybe.isPresent())
                return maybe.get();
        }

        for (var heuristic : lowQualityHeuristics) {
            var maybe = heuristic.apply(effortLevel, headers, url, document, htmlStandard);
            if (maybe.isEmpty()) {
                continue;
            }

            PubDate result = maybe.get();

            // Coerce low-resolution guesstimate to year accuracy
            if (result.hasYear()) {
                return PubDate.ofYear(result.year());
            }
            else {
                return PubDate.unknown();
            }
        }

        return PubDate.unknown();
    }

}
