package nu.marginalia.search.svc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.index.client.model.query.SearchSpecification;
import nu.marginalia.index.client.model.results.DecoratedSearchResultItem;
import nu.marginalia.query.client.QueryClient;
import nu.marginalia.search.model.UrlDetails;
import nu.marginalia.search.results.SearchResultDecorator;
import nu.marginalia.search.results.UrlDeduplicator;
import nu.marginalia.client.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.*;
import java.util.regex.Pattern;

@Singleton
public class SearchQueryIndexService {
    private final SearchResultDecorator resultDecorator;
    private final Comparator<UrlDetails> resultListComparator;
    private final QueryClient queryClient;
    private final SearchQueryCountService searchVisitorCount;
    private final Marker queryMarker = MarkerFactory.getMarker("QUERY");
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public SearchQueryIndexService(SearchResultDecorator resultDecorator,
                                   QueryClient queryClient,
                                   SearchQueryCountService searchVisitorCount) {
        this.resultDecorator = resultDecorator;
        this.queryClient = queryClient;
        this.searchVisitorCount = searchVisitorCount;

        resultListComparator = Comparator.comparing(UrlDetails::getTermScore)
                .thenComparing(UrlDetails::getRanking)
                .thenComparing(UrlDetails::getId);

    }

    public List<UrlDetails> executeQuery(Context ctx, SearchSpecification specs) {
        // Send the query
        final var queryResponse = queryClient.delegate(ctx, specs);

        // Remove duplicates and other chaff
        final var results = limitAndDeduplicateResults(specs, queryResponse.results);

        // Update the query count (this is what you see on the front page)
        searchVisitorCount.registerQuery();

        // Decorate and sort the results
        List<UrlDetails> urlDetails = resultDecorator.getAllUrlDetails(results);
        urlDetails.sort(resultListComparator);

        return urlDetails;
    }

    private List<DecoratedSearchResultItem> limitAndDeduplicateResults(SearchSpecification specs, List<DecoratedSearchResultItem> decoratedResults) {
        var limits = specs.queryLimits;

        UrlDeduplicator deduplicator = new UrlDeduplicator(limits.resultsByDomain());
        List<DecoratedSearchResultItem> retList = new ArrayList<>(limits.resultsTotal());

        int dedupCount = 0;
        for (var item : decoratedResults) {
            if (retList.size() >= limits.resultsTotal())
                break;

            if (!deduplicator.shouldRemove(item)) {
                retList.add(item);
            }
            else {
                dedupCount ++;
            }
        }

        if (dedupCount > 0) {
            logger.info(queryMarker, "Deduplicator ate {} results", dedupCount);
        }

        return retList;
    }



}
