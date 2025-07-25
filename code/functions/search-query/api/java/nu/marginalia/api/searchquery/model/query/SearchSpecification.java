package nu.marginalia.api.searchquery.model.query;

import nu.marginalia.api.searchquery.RpcQueryLimits;
import nu.marginalia.api.searchquery.RpcResultRankingParameters;
import nu.marginalia.index.query.limit.QueryStrategy;
import nu.marginalia.index.query.limit.SpecificationLimit;

import javax.annotation.Nullable;
import java.util.List;

public class SearchSpecification {
    public SearchQuery query;

    /**
     * If present and not empty, limit the search to these domain IDs
     */
    public List<Integer> domains;

    public String searchSetIdentifier;

    public SpecificationLimit quality;
    public SpecificationLimit year;
    public SpecificationLimit size;
    public SpecificationLimit rank;

    public final RpcQueryLimits queryLimits;

    public final QueryStrategy queryStrategy;

    @Nullable
    public final RpcResultRankingParameters rankingParams;

    public SearchSpecification(SearchQuery query,
                               List<Integer> domains,
                               String searchSetIdentifier,
                               SpecificationLimit quality,
                               SpecificationLimit year,
                               SpecificationLimit size,
                               SpecificationLimit rank,
                               RpcQueryLimits queryLimits,
                               QueryStrategy queryStrategy,
                               @Nullable RpcResultRankingParameters rankingParams)
    {
        this.query = query;
        this.domains = domains;
        this.searchSetIdentifier = searchSetIdentifier;
        this.quality = quality;
        this.year = year;
        this.size = size;
        this.rank = rank;
        this.queryLimits = queryLimits;
        this.queryStrategy = queryStrategy;
        this.rankingParams = rankingParams;
    }

    public static SearchSpecificationBuilder builder() {
        return new SearchSpecificationBuilder();
    }

    public SearchQuery getQuery() {
        return this.query;
    }

    public List<Integer> getDomains() {
        return this.domains;
    }

    public String getSearchSetIdentifier() {
        return this.searchSetIdentifier;
    }

    public SpecificationLimit getQuality() {
        return this.quality;
    }

    public SpecificationLimit getYear() {
        return this.year;
    }

    public SpecificationLimit getSize() {
        return this.size;
    }

    public SpecificationLimit getRank() {
        return this.rank;
    }

    public RpcQueryLimits getQueryLimits() {
        return this.queryLimits;
    }

    public QueryStrategy getQueryStrategy() {
        return this.queryStrategy;
    }

    public RpcResultRankingParameters getRankingParams() {
        return this.rankingParams;
    }

    public String toString() {
        return "SearchSpecification(query=" + this.getQuery() + ", domains=" + this.getDomains() + ", searchSetIdentifier=" + this.getSearchSetIdentifier() + ", quality=" + this.getQuality() + ", year=" + this.getYear() + ", size=" + this.getSize() + ", rank=" + this.getRank() + ", queryLimits=" + this.getQueryLimits() + ", queryStrategy=" + this.getQueryStrategy() + ", rankingParams=" + this.getRankingParams() + ")";
    }

    public static class SearchSpecificationBuilder {
        private SearchQuery query;
        private List<Integer> domains;
        private String searchSetIdentifier;
        private SpecificationLimit quality$value;
        private boolean quality$set;
        private SpecificationLimit year$value;
        private boolean year$set;
        private SpecificationLimit size$value;
        private boolean size$set;
        private SpecificationLimit rank$value;
        private boolean rank$set;
        private RpcQueryLimits queryLimits;
        private QueryStrategy queryStrategy;
        private RpcResultRankingParameters rankingParams;

        SearchSpecificationBuilder() {
        }

        public SearchSpecificationBuilder query(SearchQuery query) {
            this.query = query;
            return this;
        }

        public SearchSpecificationBuilder domains(List<Integer> domains) {
            this.domains = domains;
            return this;
        }

        public SearchSpecificationBuilder searchSetIdentifier(String searchSetIdentifier) {
            this.searchSetIdentifier = searchSetIdentifier;
            return this;
        }

        public SearchSpecificationBuilder quality(SpecificationLimit quality) {
            this.quality$value = quality;
            this.quality$set = true;
            return this;
        }

        public SearchSpecificationBuilder year(SpecificationLimit year) {
            this.year$value = year;
            this.year$set = true;
            return this;
        }

        public SearchSpecificationBuilder size(SpecificationLimit size) {
            this.size$value = size;
            this.size$set = true;
            return this;
        }

        public SearchSpecificationBuilder rank(SpecificationLimit rank) {
            this.rank$value = rank;
            this.rank$set = true;
            return this;
        }

        public SearchSpecificationBuilder queryLimits(RpcQueryLimits queryLimits) {
            this.queryLimits = queryLimits;
            return this;
        }

        public SearchSpecificationBuilder queryStrategy(QueryStrategy queryStrategy) {
            this.queryStrategy = queryStrategy;
            return this;
        }

        public SearchSpecificationBuilder rankingParams(RpcResultRankingParameters rankingParams) {
            this.rankingParams = rankingParams;
            return this;
        }

        public SearchSpecification build() {
            SpecificationLimit quality$value = this.quality$value;
            if (!this.quality$set) {
                quality$value = SpecificationLimit.none();
            }
            SpecificationLimit year$value = this.year$value;
            if (!this.year$set) {
                year$value = SpecificationLimit.none();
            }
            SpecificationLimit size$value = this.size$value;
            if (!this.size$set) {
                size$value = SpecificationLimit.none();
            }
            SpecificationLimit rank$value = this.rank$value;
            if (!this.rank$set) {
                rank$value = SpecificationLimit.none();
            }
            return new SearchSpecification(this.query, this.domains, this.searchSetIdentifier, quality$value, year$value, size$value, rank$value, this.queryLimits, this.queryStrategy, this.rankingParams);
        }
    }
}
