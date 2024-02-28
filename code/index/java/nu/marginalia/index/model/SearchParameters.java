package nu.marginalia.index.model;

import nu.marginalia.api.searchquery.IndexProtobufCodec;
import nu.marginalia.api.searchquery.RpcIndexQuery;
import nu.marginalia.api.searchquery.model.query.SearchSpecification;
import nu.marginalia.api.searchquery.model.query.SearchSubquery;
import nu.marginalia.api.searchquery.model.results.ResultRankingParameters;
import nu.marginalia.index.query.IndexSearchBudget;
import nu.marginalia.index.query.limit.QueryStrategy;
import nu.marginalia.index.searchset.SearchSet;

import java.util.ArrayList;
import java.util.List;

import static nu.marginalia.api.searchquery.IndexProtobufCodec.convertSpecLimit;

public class SearchParameters {
    /**
     * This is how many results matching the keywords we'll try to get
     * before evaluating them for the best result.
     */
    public final int fetchSize;
    public final IndexSearchBudget budget;
    public final List<SearchSubquery> subqueries;
    public final QueryParams queryParams;
    public final ResultRankingParameters rankingParams;

    public final int limitByDomain;
    public final int limitTotal;

    // mutable:

    /**
     * An estimate of how much data has been read
     */
    public long dataCost = 0;

    public SearchParameters(SearchSpecification specsSet, SearchSet searchSet) {
        var limits = specsSet.queryLimits;

        this.fetchSize = limits.fetchSize();
        this.budget = new IndexSearchBudget(limits.timeoutMs());
        this.subqueries = specsSet.subqueries;
        this.limitByDomain = limits.resultsByDomain();
        this.limitTotal = limits.resultsTotal();

        queryParams = new QueryParams(
                specsSet.quality,
                specsSet.year,
                specsSet.size,
                specsSet.rank,
                searchSet,
                specsSet.queryStrategy);

        rankingParams = specsSet.rankingParams;
    }

    public SearchParameters(RpcIndexQuery request, SearchSet searchSet) {
        var limits = IndexProtobufCodec.convertQueryLimits(request.getQueryLimits());

        this.fetchSize = limits.fetchSize();

        // The time budget is halved because this is the point when we start to
        // wrap up the search and return the results.
        this.budget = new IndexSearchBudget(limits.timeoutMs() / 2);

        this.subqueries = new ArrayList<>(request.getSubqueriesCount());
        for (int i = 0; i < request.getSubqueriesCount(); i++) {
            this.subqueries.add(IndexProtobufCodec.convertSearchSubquery(request.getSubqueries(i)));
        }
        this.limitByDomain = limits.resultsByDomain();
        this.limitTotal = limits.resultsTotal();

        queryParams = new QueryParams(
                convertSpecLimit(request.getQuality()),
                convertSpecLimit(request.getYear()),
                convertSpecLimit(request.getSize()),
                convertSpecLimit(request.getRank()),
                searchSet,
                QueryStrategy.valueOf(request.getQueryStrategy()));

        rankingParams = IndexProtobufCodec.convertRankingParameterss(request.getParameters());
    }

    public long getDataCost() {
        return dataCost;
    }

}
