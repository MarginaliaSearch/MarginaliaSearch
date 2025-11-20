package nu.marginalia.api.searchquery;

import it.unimi.dsi.fastutil.floats.FloatList;
import it.unimi.dsi.fastutil.ints.IntList;
import nu.marginalia.api.searchquery.model.query.QueryStrategy;
import nu.marginalia.api.searchquery.model.query.SpecificationLimit;

import java.util.List;

public sealed interface QueryFilterSpec {
    void configure(RpcQsQuery.Builder query);

    record NoFilter() implements QueryFilterSpec  {
        @Override
        public void configure(RpcQsQuery.Builder query) {
            // no-op
        }
    }

    record CombinedFilter(FilterByName byName, FilterAdHoc adHoc) implements QueryFilterSpec {
        @Override
        public void configure(RpcQsQuery.Builder query) {
            byName.configure(query);
            adHoc.configure(query);
        }

    }

    record FilterByName(String userId,
                        String filterIdentifier) implements QueryFilterSpec {

        @Override
        public void configure(RpcQsQuery.Builder query) {
            query.setFilterIdentifier(RpcQsFilterIdentifier.newBuilder()
                    .setUserId(userId)
                    .setIdentifier(filterIdentifier)
                    .build());
        }

    }

    record FilterAdHoc(
            IntList domainsInclude,
            IntList domainsExclude,
            IntList domainsPromote,
            FloatList domainsPromoteAmounts,
            String searchSetIdentifier,
            List<String> termsRequire,
            List<String> termsExclude,
            List<String> termsPromote,
            FloatList termsPromoteAmounts,
            SpecificationLimit quality,
            SpecificationLimit year,
            SpecificationLimit size,
            SpecificationLimit rank,
            RpcTemporalBias.Bias temporalBias,
            QueryStrategy queryStrategy
            ) implements QueryFilterSpec
    {
        public boolean isNoOp() {
            if (!domainsInclude.isEmpty())
                return false;
            if (!domainsExclude.isEmpty())
                return false;
            if (!domainsPromote.isEmpty()) // amount is always the same length
                return false;
            if (!"NONE".equals(searchSetIdentifier))
                return false;
            if (!termsRequire.isEmpty())
                return false;
            if (!termsExclude.isEmpty())
                return false;
            if (!termsPromote.isEmpty()) // amount is always the same length
                return false;
            if (!year.isNone())
                return false;
            if (!size.isNone())
                return false;
            if (!quality.isNone())
                return false;
            if (!rank.isNone())
                return false;
            if (!RpcTemporalBias.Bias.NONE.equals(temporalBias))
                return false;
            if (!QueryStrategy.AUTO.equals(queryStrategy))
                return false;

            return true;
        }

        @Override
        public void configure(RpcQsQuery.Builder query) {
            if (isNoOp())
                return;

            query.setFilterSpec(
                    RpcQsAdHocFilter.newBuilder()
                        .setHash(hashCode())
                        .addAllDomainsInclude(domainsInclude)
                        .addAllDomainsExclude(domainsExclude)
                        .addAllDomainsPromote(domainsPromote)
                        .addAllDomainsPromoteAmounts(domainsPromoteAmounts)
                        .setSearchSetIdentifier(searchSetIdentifier)
                        .addAllTermsRequire(termsRequire)
                        .addAllTermsExclude(termsExclude)
                        .addAllTermsPromote(termsPromote)
                        .addAllTermsPromoteAmounts(termsPromoteAmounts)
                        .setQuality(IndexProtobufCodec.convertSpecLimit(quality))
                        .setYear(IndexProtobufCodec.convertSpecLimit(year))
                        .setSize(IndexProtobufCodec.convertSpecLimit(size))
                        .setRank(IndexProtobufCodec.convertSpecLimit(rank))
                        .setBias(RpcTemporalBias.newBuilder().setBias(temporalBias))
            );

        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private IntList domainsInclude = IntList.of();
            private IntList domainsExclude = IntList.of();
            private IntList domainsPromote = IntList.of();
            private FloatList domainsPromoteAmounts = FloatList.of();
            private String searchSetIdentifier = "NONE";
            private List<String> termsRequire = List.of();
            private List<String> termsExclude = List.of();
            private List<String> termsPromote = List.of();
            private FloatList termsPromoteAmounts = FloatList.of();
            private SpecificationLimit quality = SpecificationLimit.none();
            private SpecificationLimit year = SpecificationLimit.none();
            private SpecificationLimit size = SpecificationLimit.none();
            private SpecificationLimit rank = SpecificationLimit.none();
            private RpcTemporalBias.Bias temporalBias = RpcTemporalBias.Bias.NONE;
            private QueryStrategy queryStrategy = QueryStrategy.AUTO;

            public Builder domainsInclude(IntList domainsInclude) {
                this.domainsInclude = domainsInclude;
                return this;
            }

            public Builder domainsExclude(IntList domainsExclude) {
                this.domainsExclude = domainsExclude;
                return this;
            }

            public Builder domainsPromote(IntList domainsPromote) {
                this.domainsPromote = domainsPromote;
                return this;
            }

            public Builder domainsPromoteAmounts(FloatList domainsPromoteAmounts) {
                this.domainsPromoteAmounts = domainsPromoteAmounts;
                return this;
            }

            public Builder searchSetIdentifier(String searchSetIdentifier) {
                this.searchSetIdentifier = searchSetIdentifier;
                return this;
            }

            public Builder termsRequire(List<String> termsRequire) {
                this.termsRequire = termsRequire;
                return this;
            }

            public Builder termsExclude(List<String> termsExclude) {
                this.termsExclude = termsExclude;
                return this;
            }

            public Builder termsPromote(List<String> termsPromote) {
                this.termsPromote = termsPromote;
                return this;
            }

            public Builder termsPromoteAmounts(FloatList termsPromoteAmounts) {
                this.termsPromoteAmounts = termsPromoteAmounts;
                return this;
            }

            public Builder quality(SpecificationLimit quality) {
                this.quality = quality;
                return this;
            }

            public Builder year(SpecificationLimit year) {
                this.year = year;
                return this;
            }

            public Builder size(SpecificationLimit size) {
                this.size = size;
                return this;
            }

            public Builder rank(SpecificationLimit rank) {
                this.rank = rank;
                return this;
            }

            public Builder temporalBias(RpcTemporalBias.Bias temporalBias) {
                this.temporalBias = temporalBias;
                return this;
            }

            public Builder queryStrategy(QueryStrategy queryStrategy) {
                this.queryStrategy = queryStrategy;
                return this;
            }

            public FilterAdHoc build() {
                return new FilterAdHoc(
                        domainsInclude,
                        domainsExclude,
                        domainsPromote,
                        domainsPromoteAmounts,
                        searchSetIdentifier,
                        termsRequire,
                        termsExclude,
                        termsPromote,
                        termsPromoteAmounts,
                        quality,
                        year,
                        size,
                        rank,
                        temporalBias,
                        queryStrategy
                );
            }
        }
    }

}
