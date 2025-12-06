package nu.marginalia.api.searchquery.model.query;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.floats.FloatList;
import nu.marginalia.api.searchquery.RpcQueryLimits;
import nu.marginalia.api.searchquery.RpcTemporalBias;

import javax.annotation.Nullable;
import java.util.List;

public record QueryParams(
        String humanQuery,
        @Nullable
        String nearDomain,
        List<String> tacitIncludes,
        List<String> tacitExcludes,
        List<String> tacitPriority,
        FloatList tacitPriorityWeights,
        List<String> tacitAdvice,
        SpecificationLimit quality,
        SpecificationLimit year,
        SpecificationLimit size,
        SpecificationLimit rank,
        List<Integer> domainIds,
        RpcQueryLimits limits,
        String identifier,
        QueryStrategy queryStrategy,
        RpcTemporalBias.Bias temporalBias,
        NsfwFilterTier filterTier,
        String langIsoCode,
        int page
)
{
    public QueryParams(String query, RpcQueryLimits limits, String identifier, NsfwFilterTier filterTier, String langIsoCode) {
        this(query, null,
                List.of(),
                List.of(),
                List.of(),
                FloatList.of(),
                List.of(),
                SpecificationLimit.none(),
                SpecificationLimit.none(),
                SpecificationLimit.none(),
                SpecificationLimit.none(),
                List.of(),
                limits,
                identifier,
                QueryStrategy.AUTO,
                RpcTemporalBias.Bias.NONE,
                filterTier,
                langIsoCode,
                1 // page
                );
    }
}
