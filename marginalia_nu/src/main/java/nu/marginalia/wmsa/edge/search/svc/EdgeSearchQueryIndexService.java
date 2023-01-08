package nu.marginalia.wmsa.edge.search.svc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.edge.index.client.EdgeIndexClient;
import nu.marginalia.wmsa.edge.index.model.QueryStrategy;
import nu.marginalia.wmsa.edge.model.search.*;
import nu.marginalia.wmsa.edge.model.search.domain.SpecificationLimit;
import nu.marginalia.wmsa.edge.search.model.EdgeSearchProfile;
import nu.marginalia.wmsa.edge.search.query.model.EdgeSearchQuery;
import nu.marginalia.wmsa.edge.search.results.SearchResultDecorator;
import nu.marginalia.wmsa.edge.search.results.UrlDeduplicator;

import java.util.*;
import java.util.regex.Pattern;

@Singleton
public class EdgeSearchQueryIndexService {

    private final SearchResultDecorator resultDecorator;
    private final Comparator<EdgeUrlDetails> resultListComparator;
    private final EdgeIndexClient indexClient;

    @Inject
    public EdgeSearchQueryIndexService(SearchResultDecorator resultDecorator, EdgeIndexClient indexClient) {
        this.resultDecorator = resultDecorator;
        this.indexClient = indexClient;

        Comparator<EdgeUrlDetails> c = Comparator.comparing(ud -> Math.round(10*(ud.getTermScore() - ud.rankingIdAdjustment())));
        resultListComparator = c
                .thenComparing(EdgeUrlDetails::getRanking)
                .thenComparing(EdgeUrlDetails::getId);
    }

    public List<EdgeUrlDetails> performDumbQuery(Context ctx, EdgeSearchProfile profile, int limitPerDomain, int limitTotal, String... termsInclude) {
        List<EdgeSearchSubquery> sqs = new ArrayList<>();

        sqs.add(new EdgeSearchSubquery(
                Arrays.asList(termsInclude),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList()
        ));

        var specs = EdgeSearchSpecification.builder()
                .subqueries(sqs)
                .domains(Collections.emptyList())
                .searchSetIdentifier(profile.searchSetIdentifier)
                .limitByDomain(limitPerDomain)
                .limitTotal(limitTotal)
                .humanQuery("")
                .timeoutMs(150)
                .fetchSize(2048)
                .year(SpecificationLimit.none())
                .size(SpecificationLimit.none())
                .quality(SpecificationLimit.none())
                .queryStrategy(QueryStrategy.AUTO)
                .build();

        return performQuery(ctx, new EdgeSearchQuery(specs));
    }

    public List<EdgeUrlDetails> performQuery(Context ctx, EdgeSearchQuery processedQuery) {

        final List<EdgeSearchResultItem> results = indexClient.query(ctx, processedQuery.specs);

        final List<EdgeUrlDetails> resultList = new ArrayList<>(results.size());

        long badQCount = 0;
        for (var details : resultDecorator.getAllUrlDetails(results)) {
            if (details.getUrlQuality() <= -100) {
                badQCount++;
                continue;
            }

            details = details.withUrlQualityAdjustment(
                    adjustScoreBasedOnQuery(details, processedQuery.specs));

            resultList.add(details);
        }

        resultList.sort(resultListComparator);

        UrlDeduplicator deduplicator = new UrlDeduplicator(processedQuery.specs.limitByDomain);
        List<EdgeUrlDetails> retList = new ArrayList<>(processedQuery.specs.limitTotal);

        if (badQCount > 0) {
            System.out.println(badQCount);
        }
        for (var item : resultList) {
            if (retList.size() >= processedQuery.specs.limitTotal)
                break;

            if (!deduplicator.shouldRemove(item)) {
                retList.add(item);
            }
        }

        return retList;
    }

    private final Pattern titleSplitPattern = Pattern.compile("[:!|./]|(\\s-|-\\s)|\\s{2,}");

    private EdgePageScoreAdjustment adjustScoreBasedOnQuery(EdgeUrlDetails p, EdgeSearchSpecification specs) {
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

        return EdgePageScoreAdjustment.builder()
                .descAdj(Math.min(termCount, descHits) / (10. * termCount))
                .descHitsAdj(descHitsAdj / 10.)
                .domainAdj(2 * Math.min(termCount, domainHits) / (double) termCount)
                .urlAdj(Math.min(termCount, urlHits) / (10. * termCount))
                .titleAdj(5 * titleHitsAdj / (Math.max(1, titleParts.length) * Math.log(titleLC.length() + 2)))
                .titleFullHit(titleFullHit)
                .build();
    }

}
