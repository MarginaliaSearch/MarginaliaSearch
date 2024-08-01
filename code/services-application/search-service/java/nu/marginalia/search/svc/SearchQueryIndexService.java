package nu.marginalia.search.svc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import it.unimi.dsi.fastutil.ints.Int2LongArrayMap;
import lombok.SneakyThrows;
import nu.marginalia.bbpc.BrailleBlockPunchCards;
import nu.marginalia.api.searchquery.model.query.SearchSpecification;
import nu.marginalia.api.searchquery.model.results.DecoratedSearchResultItem;
import nu.marginalia.api.searchquery.model.results.SearchResultItem;
import nu.marginalia.model.crawl.DomainIndexingState;
import nu.marginalia.api.searchquery.model.query.QueryResponse;
import nu.marginalia.search.model.UrlDetails;
import nu.marginalia.search.results.UrlDeduplicator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.*;

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
        // Remove duplicates and other chaff
        final var results = limitAndDeduplicateResults(queryResponse.specs(), queryResponse.results());

        // Update the query count (this is what you see on the front page)
        searchVisitorCount.registerQuery();

        // Decorate and sort the results
        List<UrlDetails> urlDetails = getAllUrlDetails(results);

        urlDetails.sort(Comparator.naturalOrder());

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


    @SneakyThrows
    public List<UrlDetails> getAllUrlDetails(List<DecoratedSearchResultItem> resultSet) {
        List<UrlDetails> ret = new ArrayList<>(resultSet.size());

        for (var detail : resultSet) {
            ret.add(new UrlDetails(
                    detail.documentId(),
                    detail.domainId(),
                    detail.url,
                    detail.title,
                    detail.description,
                    detail.format,
                    detail.features,
                    DomainIndexingState.ACTIVE,
                    detail.rankingScore, // termScore
                    detail.resultsFromDomain,
                    getPositionsString(detail),
                    Long.bitCount(detail.bestPositions),
                    detail.rawIndexResult,
                    detail.rawIndexResult.keywordScores
            ));
        }

        return ret;
    }

    private String getPositionsString(DecoratedSearchResultItem resultItem) {
        return BrailleBlockPunchCards.printBits(resultItem.bestPositions, 56);

    }
}
