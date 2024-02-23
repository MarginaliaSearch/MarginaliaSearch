package nu.marginalia.index.model;

import gnu.trove.set.hash.TLongHashSet;
import nu.marginalia.api.searchquery.RpcIndexQuery;
import nu.marginalia.api.searchquery.model.query.SearchSpecification;
import nu.marginalia.api.searchquery.model.query.SearchSubquery;
import nu.marginalia.api.searchquery.model.results.ResultRankingParameters;
import nu.marginalia.api.searchquery.IndexProtobufCodec;
import nu.marginalia.index.index.StatefulIndex;
import nu.marginalia.index.query.IndexQuery;
import nu.marginalia.index.query.IndexSearchBudget;
import nu.marginalia.index.query.limit.QueryStrategy;
import nu.marginalia.index.searchset.SearchSet;

import java.util.ArrayList;
import java.util.List;

public class IndexSearchParameters {
    /**
     * This is how many results matching the keywords we'll try to get
     * before evaluating them for the best result.
     */
    public final int fetchSize;
    public final IndexSearchBudget budget;
    public final List<SearchSubquery> subqueries;
    public final IndexQueryParams queryParams;
    public final ResultRankingParameters rankingParams;

    public final int limitByDomain;
    public final int limitTotal;

    // mutable:

    /**
     * An estimate of how much data has been read
     */
    public long dataCost = 0;

    /**
     * A set of id:s considered during each subquery,
     * for deduplication
     */
    public final TLongHashSet consideredUrlIds;

    public IndexSearchParameters(SearchSpecification specsSet, SearchSet searchSet) {
        var limits = specsSet.queryLimits;

        this.fetchSize = limits.fetchSize();
        this.budget = new IndexSearchBudget(limits.timeoutMs());
        this.subqueries = specsSet.subqueries;
        this.limitByDomain = limits.resultsByDomain();
        this.limitTotal = limits.resultsTotal();

        this.consideredUrlIds = CachedObjects.getConsideredUrlsMap();

        queryParams = new IndexQueryParams(
                specsSet.quality,
                specsSet.year,
                specsSet.size,
                specsSet.rank,
                specsSet.domainCount,
                searchSet,
                specsSet.queryStrategy);

        rankingParams = specsSet.rankingParams;
    }

    public IndexSearchParameters(RpcIndexQuery request, SearchSet searchSet) {
        var limits = IndexProtobufCodec.convertQueryLimits(request.getQueryLimits());

        this.fetchSize = limits.fetchSize();
        this.budget = new IndexSearchBudget(limits.timeoutMs());
        this.subqueries = new ArrayList<>(request.getSubqueriesCount());
        for (int i = 0; i < request.getSubqueriesCount(); i++) {
            this.subqueries.add(IndexProtobufCodec.convertSearchSubquery(request.getSubqueries(i)));
        }
        this.limitByDomain = limits.resultsByDomain();
        this.limitTotal = limits.resultsTotal();

        this.consideredUrlIds = CachedObjects.getConsideredUrlsMap();

        queryParams = new IndexQueryParams(
                IndexProtobufCodec.convertSpecLimit(request.getQuality()),
                IndexProtobufCodec.convertSpecLimit(request.getYear()),
                IndexProtobufCodec.convertSpecLimit(request.getSize()),
                IndexProtobufCodec.convertSpecLimit(request.getRank()),
                IndexProtobufCodec.convertSpecLimit(request.getDomainCount()),
                searchSet,
                QueryStrategy.valueOf(request.getQueryStrategy()));

        rankingParams = IndexProtobufCodec.convertRankingParameterss(request.getParameters());
    }

    public List<IndexQuery> createIndexQueries(StatefulIndex index, IndexSearchTerms terms) {
        return index.createQueries(terms, queryParams, consideredUrlIds::add);
    }

    public boolean hasTimeLeft() {
        return budget.hasTimeLeft();
    }

    public long getDataCost() {
        return dataCost;
    }

    private static class CachedObjects {
        private static final ThreadLocal<TLongHashSet> consideredCache = ThreadLocal.withInitial(() -> new TLongHashSet(4096));
        private static TLongHashSet getConsideredUrlsMap() {
            var ret = consideredCache.get();
            ret.clear();
            return ret;
        }
    }
}
