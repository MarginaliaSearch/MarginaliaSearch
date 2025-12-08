package nu.marginalia.api.searchquery.model;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.floats.FloatList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import nu.marginalia.api.searchquery.IndexProtobufCodec;
import nu.marginalia.api.searchquery.RpcQsAdHocFilter;
import nu.marginalia.api.searchquery.model.query.QueryStrategy;
import nu.marginalia.api.searchquery.model.query.SpecificationLimit;

import java.util.ArrayList;
import java.util.List;

public record CompiledSearchFilterSpec(String userId,
                                       String identifier,
                                       IntList domainsInclude,
                                       IntList domainsExclude,
                                       IntList domainsPromote,
                                       FloatList domainsPromoteAmounts,
                                       String searchSetIdentifier,
                                       List<String> termsRequire,
                                       List<String> termsExclude,
                                       List<String> termsPromote,
                                       FloatList termsPromoteAmounts,
                                       SpecificationLimit year,
                                       SpecificationLimit size,
                                       SpecificationLimit quality,
                                       SpecificationLimit rank,
                                       String temporalBias,
                                       QueryStrategy queryStrategy
                                       )
{
    
    public CompiledSearchFilterSpec(RpcQsAdHocFilter rpcVariant) {
        this("AD-HOC",
            Long.toHexString(rpcVariant.getHash()),
            new IntArrayList(rpcVariant.getDomainsIncludeList()),
            new IntArrayList(rpcVariant.getDomainsExcludeList()),
            new IntArrayList(rpcVariant.getDomainsPromoteList()),
            new FloatArrayList(rpcVariant.getDomainsPromoteAmountsList()),
            rpcVariant.getSearchSetIdentifier(),
            rpcVariant.getTermsRequireList(),
            rpcVariant.getTermsExcludeList(),
            rpcVariant.getTermsPromoteList(),
            new FloatArrayList(rpcVariant.getTermsPromoteAmountsList()),
            IndexProtobufCodec.convertSpecLimit(rpcVariant.getYear()),
            IndexProtobufCodec.convertSpecLimit(rpcVariant.getSize()),
            IndexProtobufCodec.convertSpecLimit(rpcVariant.getQuality()),
            IndexProtobufCodec.convertSpecLimit(rpcVariant.getRank()),
            rpcVariant.getBias().getBias().name(),
            convertQueryStrategy(rpcVariant.getQueryStrategy())
        );
    }
    
    private static QueryStrategy convertQueryStrategy(String namedStr) {
        if ("".equals(namedStr)) {
            return QueryStrategy.AUTO;
        }
        try {
            return QueryStrategy.valueOf(namedStr);
        }
        catch (IllegalArgumentException ex) {
            return QueryStrategy.AUTO;
        }
    }

    public static Builder builder(String userId, String identifier) {
        return new Builder(userId, identifier);
    }

    public static CompiledSearchFilterSpec merge(CompiledSearchFilterSpec base, CompiledSearchFilterSpec modifications) {
        return new CompiledSearchFilterSpec(
                "AD-HOC",
                base.identifier + ":" + modifications.identifier,
                joinList(base.domainsInclude, modifications.domainsInclude),
                joinList(base.domainsExclude, modifications.domainsExclude),
                joinList(base.domainsPromote, modifications.domainsPromote),
                joinList(base.domainsPromoteAmounts, modifications.domainsPromoteAmounts),
                mergeStrings(base.searchSetIdentifier, modifications.searchSetIdentifier, "NONE"),
                joinList(base.termsRequire, modifications.termsRequire),
                joinList(base.termsExclude, modifications.termsExclude),
                joinList(base.termsPromote, modifications.termsPromote),
                joinList(base.termsPromoteAmounts, modifications.termsPromoteAmounts),
                mergeSpecLimits(base.year, modifications.year),
                mergeSpecLimits(base.size, modifications.size),
                mergeSpecLimits(base.quality, modifications.quality),
                mergeSpecLimits(base.rank, modifications.rank),
                mergeStrings(base.temporalBias, modifications.temporalBias, "NONE"),
                mergeQueryStrategy(base.queryStrategy, modifications.queryStrategy)
        );
    }

    private static IntList joinList(IntList a, IntList b) {
        if (b.isEmpty()) return a;
        if (a.isEmpty()) return b;
        IntArrayList combined = new IntArrayList(a.size() + b.size());
        combined.addAll(a);
        combined.addAll(b);
        return combined;
    }

    private static FloatList joinList(FloatList a, FloatList b) {
        if (b.isEmpty()) return a;
        if (a.isEmpty()) return b;
        FloatArrayList combined = new FloatArrayList(a.size() + b.size());
        combined.addAll(a);
        combined.addAll(b);
        return combined;
    }

    private static List<String> joinList(List<String> a, List<String> b) {
        if (b.isEmpty()) return a;
        if (a.isEmpty()) return b;
        ArrayList<String> combined = new ArrayList<>(a.size() + b.size());
        combined.addAll(a);
        combined.addAll(b);
        return combined;
    }


    private static QueryStrategy mergeQueryStrategy(QueryStrategy a, QueryStrategy b) {
        if (b == QueryStrategy.AUTO)
            return a;
        return b;
    }

    private static String mergeStrings(String a, String b, String emptyVal) {
        if (emptyVal.equals(b)) return a;
        return b;
    }


    private static SpecificationLimit mergeSpecLimits(SpecificationLimit base, SpecificationLimit mod) {
        if (mod.isNone())
            return base;
        return mod;
    }

    public static class Builder {
        private final String userId;
        private final String identifier;
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
        private String temporalBias = "NONE";
        private QueryStrategy queryStrategy = QueryStrategy.AUTO;

        public Builder(String userId, String identifer) {
            this.userId = userId;
            this.identifier = identifer;
        }

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

        public Builder temporalBias(String temporalBias) {
            this.temporalBias = temporalBias;
            return this;
        }

        public Builder queryStrategy(QueryStrategy queryStrategy) {
            this.queryStrategy = queryStrategy;
            return this;
        }



        public CompiledSearchFilterSpec build() {
            return new CompiledSearchFilterSpec(
                    userId,
                    identifier,
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
