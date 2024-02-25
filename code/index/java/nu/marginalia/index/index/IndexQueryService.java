package nu.marginalia.index.index;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import nu.marginalia.api.searchquery.model.query.SearchSubquery;
import nu.marginalia.array.buffer.LongQueryBuffer;
import nu.marginalia.index.model.QueryParams;
import nu.marginalia.index.model.SearchTerms;
import nu.marginalia.index.query.IndexQuery;
import nu.marginalia.index.query.IndexSearchBudget;
import nu.marginalia.index.results.model.ids.CombinedDocIdList;
import org.roaringbitmap.longlong.Roaring64Bitmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.List;
import java.util.function.Consumer;

@Singleton
public class IndexQueryService {
    private final Marker queryMarker = MarkerFactory.getMarker("QUERY");

    private static final Logger logger = LoggerFactory.getLogger(IndexQueryService.class);
    private final StatefulIndex index;

    @Inject
    public IndexQueryService(StatefulIndex index) {
        this.index = index;
    }

    /** Execute subqueries and return a list of document ids.  The index is queried for each subquery,
     * at different priorty depths until timeout is reached or the results are all visited.
     * Then the results are combined.
     * */
    public void evaluateSubquery(SearchSubquery subquery,
                                 QueryParams queryParams,
                                 IndexSearchBudget timeout,
                                 int fetchSize,
                                 Consumer<CombinedDocIdList> drain)
    {
        // These queries are various term combinations

        if (!timeout.hasTimeLeft()) {
            logger.info("Query timed out {}, ({}), -{}",
                    subquery.searchTermsInclude, subquery.searchTermsAdvice, subquery.searchTermsExclude);
            return;
        }
        logger.info(queryMarker, "{}", subquery);

        final SearchTerms searchTerms = new SearchTerms(subquery);
        if (searchTerms.isEmpty()) {
            logger.info(queryMarker, "empty");
            return;
        }

        final Roaring64Bitmap results = new Roaring64Bitmap();

        // logSearchTerms(subquery, searchTerms);

        // These queries are different indices for one subquery
        List<IndexQuery> queries = index.createQueries(searchTerms, queryParams);
        for (var query : queries) {

            if (!timeout.hasTimeLeft())
                break;

            final LongQueryBuffer buffer = new LongQueryBuffer(512);

            while (query.hasMore()
                    && results.getIntCardinality() < fetchSize * query.fetchSizeMultiplier
                    && timeout.hasTimeLeft())
            {
                buffer.reset();
                query.getMoreResults(buffer);

                for (int i = 0; i < buffer.size(); i++) {
                    results.add(buffer.data[i]);
                }

                if (results.getIntCardinality() > 512) {
                    drain.accept(new CombinedDocIdList(results));
                    results.clear();
                }
            }

            logger.info(queryMarker, "{} from {}", results.getIntCardinality(), query);
        }

        if (!results.isEmpty()) {
            drain.accept(new CombinedDocIdList(results));
        }
    }

    private void logSearchTerms(SearchSubquery subquery, SearchTerms searchTerms) {

        // This logging should only be enabled in testing, as it is very verbose
        // and contains sensitive information

        if (!logger.isInfoEnabled(queryMarker)) {
            return;
        }

        var includes = subquery.searchTermsInclude;
        var advice = subquery.searchTermsAdvice;
        var excludes = subquery.searchTermsExclude;
        var priority = subquery.searchTermsPriority;

        for (int i = 0; i < includes.size(); i++) {
            logger.info(queryMarker, "{} -> {} I", includes.get(i),
                    Long.toHexString(searchTerms.includes().getLong(i))
            );
        }
        for (int i = 0; i < advice.size(); i++) {
            logger.info(queryMarker, "{} -> {} A", advice.get(i),
                    Long.toHexString(searchTerms.includes().getLong(includes.size() + i))
            );
        }
        for (int i = 0; i < excludes.size(); i++) {
            logger.info(queryMarker, "{} -> {} E", excludes.get(i),
                    Long.toHexString(searchTerms.excludes().getLong(i))
            );
        }
        for (int i = 0; i < priority.size(); i++) {
            logger.info(queryMarker, "{} -> {} P", priority.get(i),
                    Long.toHexString(searchTerms.priority().getLong(i))
            );
        }
    }


}
