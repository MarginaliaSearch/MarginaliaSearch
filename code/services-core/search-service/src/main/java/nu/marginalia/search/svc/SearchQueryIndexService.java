package nu.marginalia.search.svc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.index.client.IndexClient;
import nu.marginalia.index.client.model.results.SearchResultItem;
import nu.marginalia.index.client.model.query.SearchSpecification;
import nu.marginalia.index.client.model.results.SearchResultSet;
import nu.marginalia.search.model.PageScoreAdjustment;
import nu.marginalia.search.model.UrlDetails;
import nu.marginalia.search.results.SearchResultDecorator;
import nu.marginalia.search.results.UrlDeduplicator;
import nu.marginalia.client.Context;
import nu.marginalia.search.query.model.SearchQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;

@Singleton
public class SearchQueryIndexService {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final SearchResultDecorator resultDecorator;
    private final Comparator<UrlDetails> resultListComparator;
    private final IndexClient indexClient;

    @Inject
    public SearchQueryIndexService(SearchResultDecorator resultDecorator, IndexClient indexClient) {
        this.resultDecorator = resultDecorator;
        this.indexClient = indexClient;

        Comparator<UrlDetails> c = Comparator.comparing(ud -> Math.round(10*(ud.getTermScore() - ud.rankingIdAdjustment())));
        resultListComparator = c
                .thenComparing(UrlDetails::getRanking)
                .thenComparing(UrlDetails::getId);
    }

    public List<UrlDetails> executeQuery(Context ctx, SearchQuery processedQuery) {
        final SearchResultSet results = indexClient.query(ctx, processedQuery.specs);

        List<UrlDetails> urlDetails = resultDecorator.getAllUrlDetails(results);

        urlDetails.replaceAll(details ->
                details.withUrlQualityAdjustment(adjustScoreBasedOnQuery(details, processedQuery.specs))
        );

        urlDetails.sort(resultListComparator);

        return limitAndDeduplicateResults(processedQuery, urlDetails);
    }

    private List<UrlDetails> limitAndDeduplicateResults(SearchQuery processedQuery, List<UrlDetails> decoratedResults) {
        var limits = processedQuery.specs.queryLimits;

        UrlDeduplicator deduplicator = new UrlDeduplicator(limits.resultsByDomain());
        List<UrlDetails> retList = new ArrayList<>(limits.resultsTotal());

        for (var item : decoratedResults) {
            if (retList.size() >= limits.resultsTotal())
                break;

            if (!deduplicator.shouldRemove(item)) {
                retList.add(item);
            }
        }

        return retList;
    }

    private final Pattern titleSplitPattern = Pattern.compile("[:!|./]|(\\s-|-\\s)|\\s{2,}");

    private PageScoreAdjustment adjustScoreBasedOnQuery(UrlDetails p, SearchSpecification specs) {
        String titleLC = p.title == null ? "" : p.title.toLowerCase();
        String descLC = p.description == null ? "" : p.description.toLowerCase();
        String urlLC = p.url == null ? "" : p.url.path.toLowerCase();
        String domainLC = p.url == null ? "" : p.url.domain.toString().toLowerCase();

        String[] searchTermsLC = specs.subqueries.get(0).searchTermsInclude.stream()
                .map(String::toLowerCase)
                .flatMap(s -> Arrays.stream(s.split("_")))
                .toArray(String[]::new);
        int termCount = searchTermsLC.length;

        double titleHitsAdj = 0.;
        final String[] titleParts = titleSplitPattern.split(titleLC);
        for (String titlePart : titleParts) {
            double hits = 0;
            for (String term : searchTermsLC) {
                if (titlePart.contains(term)) {
                    hits += term.length();
                }
            }
            titleHitsAdj += hits / Math.max(1, titlePart.length());
        }

        double titleFullHit = 0.;
        if (termCount > 1 && titleLC.contains(specs.humanQuery.replaceAll("\"", "").toLowerCase())) {
            titleFullHit = termCount;
        }
        long descHits = Arrays.stream(searchTermsLC).filter(descLC::contains).count();
        long urlHits = Arrays.stream(searchTermsLC).filter(urlLC::contains).count();
        long domainHits = Arrays.stream(searchTermsLC).filter(domainLC::contains).count();

        double descHitsAdj = 0.;
        for (String word : descLC.split("\\W+")) {
            descHitsAdj += Arrays.stream(searchTermsLC)
                    .filter(term -> term.length() > word.length())
                    .filter(term -> term.contains(word))
                    .mapToDouble(term -> word.length() / (double) term.length())
                    .sum();
        }

        return PageScoreAdjustment.builder()
                .descAdj(Math.min(termCount, descHits) / (10. * termCount))
                .descHitsAdj(descHitsAdj / 10.)
                .domainAdj(2 * Math.min(termCount, domainHits) / (double) termCount)
                .urlAdj(Math.min(termCount, urlHits) / (10. * termCount))
                .titleAdj(5 * titleHitsAdj / (Math.max(1, titleParts.length) * Math.log(titleLC.length() + 2)))
                .titleFullHit(titleFullHit)
                .build();
    }

}
