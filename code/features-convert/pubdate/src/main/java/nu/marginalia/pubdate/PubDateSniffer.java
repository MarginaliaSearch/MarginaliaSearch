package nu.marginalia.pubdate;

import nu.marginalia.converting.model.HtmlStandard;
import nu.marginalia.model.crawl.PubDate;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.pubdate.heuristic.*;
import org.jsoup.nodes.Document;

import java.util.ArrayList;
import java.util.List;

public class PubDateSniffer {

    private final List<PubDateHeuristic> heuristics = new ArrayList<>();

    public PubDateSniffer() {
        heuristics.add(new PubDateHeuristicJSONLD());
        heuristics.add(new PubDateHeuristicMicrodata());
        heuristics.add(new PubDateHeuristicOpenGraph());
        heuristics.add(new PubDateHeuristicRDFaTag());

        heuristics.add(new PubDateHeuristicHtml5ItempropDateTag());
        heuristics.add(new PubDateHeuristicHtml5ArticleDateTag());

        // The more questionable heuristics should be kept below this line
        heuristics.add(new PubDateHeuristicUrlPatternPass1());

        heuristics.add(new PubDateHeuristicDOMParsingPass1());
        heuristics.add(new PubDateHeuristicHtml5AnyTimeTag());

        heuristics.add(new PubDateHeuristicDOMParsingPass2());
        heuristics.add(new PubDateHeuristicUrlPatternPass2());

        heuristics.add(new PubDateHeuristicLastModified());
        // This is complete guesswork

        heuristics.add(new PubDateHeuristicGuessFromHtmlStandard());
    }

    public PubDate getPubDate(String headers, EdgeUrl url, Document document, HtmlStandard htmlStandard, boolean runExpensive) {
        final PubDateEffortLevel effortLevel = runExpensive ? PubDateEffortLevel.HIGH : PubDateEffortLevel.LOW;

        for (var heuristic : heuristics) {
            var maybe = heuristic.apply(effortLevel, headers, url, document, htmlStandard);
            if (maybe.isPresent())
                return maybe.get();
        }

        return new PubDate();
    }

}
