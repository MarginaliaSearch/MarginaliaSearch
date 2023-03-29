package nu.marginalia.index.svc;

import gnu.trove.set.hash.TLongHashSet;
import nu.marginalia.index.client.model.query.SearchSpecification;
import nu.marginalia.index.client.model.query.SearchSubquery;
import nu.marginalia.index.index.SearchIndex;
import nu.marginalia.index.index.SearchIndexSearchTerms;
import nu.marginalia.index.query.IndexQuery;
import nu.marginalia.index.query.IndexQueryParams;
import nu.marginalia.index.query.IndexSearchBudget;
import nu.marginalia.index.searchset.SearchSet;

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

        this.consideredUrlIds = new TLongHashSet(fetchSize * 4);

        queryParams = new IndexQueryParams(
                specsSet.quality,
                specsSet.year,
                specsSet.size,
                specsSet.rank,
                searchSet,
                specsSet.queryStrategy);
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

}
