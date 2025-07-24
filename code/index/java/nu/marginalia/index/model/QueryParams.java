package nu.marginalia.index.model;

import nu.marginalia.index.query.limit.QueryStrategy;
import nu.marginalia.index.query.limit.SpecificationLimit;
import nu.marginalia.index.query.limit.SpecificationLimitType;
import nu.marginalia.index.searchset.SearchSet;

import java.util.Objects;

/**
 * IndexQueryParams is a set of parameters for a query.
 */
public final class QueryParams {
    private final SpecificationLimit qualityLimit;
    private final SpecificationLimit year;
    private final SpecificationLimit size;
    private final SpecificationLimit rank;
    private final SearchSet searchSet;
    private final QueryStrategy queryStrategy;

    /**
     * @param qualityLimit  The quality limit.
     * @param year          The year limit.
     * @param size          The size limit.  Eliminates results from domains that do not satisfy the size criteria.
     * @param rank          The rank limit.  Eliminates results from domains that do not satisfy the domain rank criteria.
     * @param searchSet     The search set.  Limits the search to a set of domains.
     * @param queryStrategy The query strategy.  May impose additional constraints on the query, such as requiring
     *                      the keywords to appear in the title, or in the domain.
     */
    public QueryParams(SpecificationLimit qualityLimit,
                       SpecificationLimit year,
                       SpecificationLimit size,
                       SpecificationLimit rank,
                       SearchSet searchSet,
                       QueryStrategy queryStrategy
    ) {
        this.qualityLimit = qualityLimit;
        this.year = year;
        this.size = size;
        this.rank = rank;
        this.searchSet = searchSet;
        this.queryStrategy = queryStrategy;
    }

    public boolean imposesDomainMetadataConstraint() {
        return qualityLimit.type() != SpecificationLimitType.NONE
                ||  year.type() != SpecificationLimitType.NONE
                ||  size.type() != SpecificationLimitType.NONE
                ||  rank.type() != SpecificationLimitType.NONE;
    }

    public SpecificationLimit qualityLimit() {
        return qualityLimit;
    }

    public SpecificationLimit year() {
        return year;
    }

    public SpecificationLimit size() {
        return size;
    }

    public SpecificationLimit rank() {
        return rank;
    }

    public SearchSet searchSet() {
        return searchSet;
    }

    public QueryStrategy queryStrategy() {
        return queryStrategy;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (QueryParams) obj;
        return Objects.equals(this.qualityLimit, that.qualityLimit) &&
                Objects.equals(this.year, that.year) &&
                Objects.equals(this.size, that.size) &&
                Objects.equals(this.rank, that.rank) &&
                Objects.equals(this.searchSet, that.searchSet) &&
                Objects.equals(this.queryStrategy, that.queryStrategy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(qualityLimit, year, size, rank, searchSet, queryStrategy);
    }

    @Override
    public String toString() {
        return "QueryParams[" +
                "qualityLimit=" + qualityLimit + ", " +
                "year=" + year + ", " +
                "size=" + size + ", " +
                "rank=" + rank + ", " +
                "searchSet=" + searchSet + ", " +
                "queryStrategy=" + queryStrategy + ']';
    }


}
