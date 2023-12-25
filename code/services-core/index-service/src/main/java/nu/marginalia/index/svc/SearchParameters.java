package nu.marginalia.index.svc;

import gnu.trove.set.hash.TLongHashSet;
import nu.marginalia.index.api.RpcIndexQuery;
import nu.marginalia.index.api.RpcSpecLimit;
import nu.marginalia.index.client.IndexProtobufCodec;
import nu.marginalia.index.client.model.query.SearchSpecification;
import nu.marginalia.index.client.model.query.SearchSubquery;
import nu.marginalia.index.client.model.results.Bm25Parameters;
import nu.marginalia.index.client.model.results.ResultRankingParameters;
import nu.marginalia.index.index.SearchIndex;
import nu.marginalia.index.index.SearchIndexSearchTerms;
import nu.marginalia.index.query.IndexQuery;
import nu.marginalia.index.query.IndexQueryParams;
import nu.marginalia.index.query.IndexSearchBudget;
import nu.marginalia.index.query.limit.QueryLimits;
import nu.marginalia.index.query.limit.QueryStrategy;
import nu.marginalia.index.query.limit.SpecificationLimit;
import nu.marginalia.index.query.limit.SpecificationLimitType;
import nu.marginalia.index.searchset.SearchSet;

import java.util.ArrayList;
import java.util.List;

public class SearchParameters {
    /**
     * This is how many results matching the keywords we'll try to get
     * before evaluating them for the best result.
     */
    final int fetchSize;
    final IndexSearchBudget budget;
    final List<SearchSubquery> subqueries;
    final IndexQueryParams queryParams;
    final ResultRankingParameters rankingParams;

    final int limitByDomain;
    final int limitTotal;

    // mutable:

    /**
     * An estimate of how much data has been read
     */
    long dataCost = 0;

    /**
     * A set of id:s considered during each subquery,
     * for deduplication
     */
    final TLongHashSet consideredUrlIds;

    public SearchParameters(SearchSpecification specsSet, SearchSet searchSet) {
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

    public SearchParameters(RpcIndexQuery request, SearchSet searchSet) {
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

    List<IndexQuery> createIndexQueries(SearchIndex index, SearchIndexSearchTerms terms) {
        return index.createQueries(terms, queryParams, consideredUrlIds::add);
    }

    boolean hasTimeLeft() {
        return budget.hasTimeLeft();
    }

    long getDataCost() {
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
