package nu.marginalia.search.svc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.api.searchquery.model.query.QueryResponse;
import nu.marginalia.api.searchquery.model.results.DecoratedSearchResultItem;
import nu.marginalia.bbpc.BrailleBlockPunchCards;
import nu.marginalia.index.query.limit.QueryLimits;
import nu.marginalia.model.crawl.DomainIndexingState;
import nu.marginalia.search.model.UrlDetails;
import nu.marginalia.search.results.UrlDeduplicator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.List;

@Singleton
public class SearchQueryIndexService {
    private final SearchQueryCountService searchVisitorCount;
    private final Marker queryMarker = MarkerFactory.getMarker("QUERY");
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public SearchQueryIndexService(SearchQueryCountService searchVisitorCount) {
        this.searchVisitorCount = searchVisitorCount;
    }

    public List<UrlDetails> getResultsFromQuery(QueryResponse queryResponse) {
        final QueryLimits limits = queryResponse.specs().queryLimits;
        final UrlDeduplicator deduplicator = new UrlDeduplicator(limits.resultsByDomain());

        // Update the query count (this is what you see on the front page)
        searchVisitorCount.registerQuery();

        return queryResponse.results().stream()
                .filter(deduplicator::shouldRetain)
                .limit(limits.resultsTotal())
                .map(SearchQueryIndexService::createDetails)
                .toList();
    }

    private static UrlDetails createDetails(DecoratedSearchResultItem item) {
        return new UrlDetails(
                item.documentId(),
                item.domainId(),
                item.url,
                item.title,
                item.description,
                item.format,
                item.features,
                DomainIndexingState.ACTIVE,
                item.rankingScore, // termScore
                item.resultsFromDomain,
                BrailleBlockPunchCards.printBits(item.bestPositions, 64),
                Long.bitCount(item.bestPositions),
                item.rawIndexResult,
                item.rawIndexResult.keywordScores
        );
    }
}
