package nu.marginalia.search.svc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.index.client.IndexClient;
import nu.marginalia.index.client.model.query.SearchSpecification;
import nu.marginalia.index.client.model.results.SearchResultSet;
import nu.marginalia.search.model.PageScoreAdjustment;
import nu.marginalia.search.model.UrlDetails;
import nu.marginalia.search.results.SearchResultDecorator;
import nu.marginalia.search.results.UrlDeduplicator;
import nu.marginalia.client.Context;
import nu.marginalia.search.query.model.SearchQuery;

import java.util.*;
import java.util.regex.Pattern;

@Singleton
public class SearchQueryIndexService {
    private final SearchResultDecorator resultDecorator;
    private final Comparator<UrlDetails> resultListComparator;
    private final IndexClient indexClient;
    private final SearchQueryCountService searchVisitorCount;

    @Inject
    public SearchQueryIndexService(SearchResultDecorator resultDecorator,
                                   IndexClient indexClient,
                                   SearchQueryCountService searchVisitorCount) {
        this.resultDecorator = resultDecorator;
        this.indexClient = indexClient;
        this.searchVisitorCount = searchVisitorCount;

        resultListComparator = Comparator.comparing(UrlDetails::getTermScore)
                .thenComparing(UrlDetails::getRanking)
                .thenComparing(UrlDetails::getId);

    }

    public List<UrlDetails> executeQuery(Context ctx, SearchQuery processedQuery) {
        final SearchResultSet results = indexClient.query(ctx, processedQuery.specs);

        searchVisitorCount.registerQuery();

        List<UrlDetails> urlDetails = resultDecorator.getAllUrlDetails(results);

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



}
