package nu.marginalia.functions.index.index;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import gnu.trove.list.TLongList;
import gnu.trove.list.array.TLongArrayList;
import nu.marginalia.api.searchquery.model.query.SearchSubquery;
import nu.marginalia.array.buffer.LongQueryBuffer;
import nu.marginalia.functions.index.model.IndexSearchTerms;
import nu.marginalia.functions.index.model.IndexSearchParameters;
import nu.marginalia.functions.index.SearchTermsUtil;
import nu.marginalia.index.query.IndexQuery;
import nu.marginalia.index.query.IndexQueryPriority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.List;

@Singleton
public class IndexQueryService {
    private final Marker queryMarker = MarkerFactory.getMarker("QUERY");

    /** Execute subqueries and return a list of document ids.  The index is queried for each subquery,
     * at different priorty depths until timeout is reached or the results are all visited.
     * <br>
     * Then the results are combined.
     * */
    private final ThreadLocal<TLongArrayList> resultsArrayListPool = ThreadLocal.withInitial(TLongArrayList::new);
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
    public TLongList evaluateSubqueries(IndexSearchParameters params) {
        final TLongArrayList results = resultsArrayListPool.get();
        results.resetQuick();
        results.ensureCapacity(params.fetchSize);

        // These queries are various term combinations
        for (var subquery : params.subqueries) {

            if (!params.hasTimeLeft()) {
                logger.info("Query timed out {}, ({}), -{}",
                        subquery.searchTermsInclude, subquery.searchTermsAdvice, subquery.searchTermsExclude);
                break;
            }

            logger.info(queryMarker, "{}", subquery);

            final IndexSearchTerms searchTerms = SearchTermsUtil.extractSearchTerms(subquery);

            if (searchTerms.isEmpty()) {
                logger.info(queryMarker, "empty");
                continue;
            }

            // logSearchTerms(subquery, searchTerms);

            // These queries are different indices for one subquery
            List<IndexQuery> queries = params.createIndexQueries(index, searchTerms);
            for (var query : queries) {

                if (!params.hasTimeLeft())
                    break;

                if (shouldOmitQuery(params, query, results.size())) {
                    logger.info(queryMarker, "Omitting {}", query);
                    continue;
                }

                final int fetchSize = params.fetchSize * query.fetchSizeMultiplier;
                final LongQueryBuffer buffer = new LongQueryBuffer(fetchSize);

                int cnt = 0;

                while (query.hasMore()
                        && results.size() < fetchSize
                        && params.budget.hasTimeLeft())
                {
                    buffer.reset();
                    query.getMoreResults(buffer);

                    for (int i = 0; i < buffer.size() && results.size() < fetchSize; i++) {
                        results.add(buffer.data[i]);
                        cnt++;
                    }
                }

                params.dataCost += query.dataCost();


                logger.info(queryMarker, "{} from {}", cnt, query);
            }
        }

        return results;
    }

    /** @see IndexQueryPriority */
    private boolean shouldOmitQuery(IndexSearchParameters params, IndexQuery query, int resultCount) {

        var priority = query.queryPriority;

        return switch (priority) {
            case IndexQueryPriority.BEST -> false;
            case IndexQueryPriority.GOOD -> resultCount > params.fetchSize / 4;
            case IndexQueryPriority.FALLBACK -> resultCount > params.fetchSize / 8;
        };
    }

    private void logSearchTerms(SearchSubquery subquery, IndexSearchTerms searchTerms) {

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
