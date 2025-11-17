package nu.marginalia.api.searchquery.model;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.floats.FloatList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import nu.marginalia.api.searchquery.IndexProtobufCodec;
import nu.marginalia.api.searchquery.RpcQsAdHocFilter;
import nu.marginalia.api.searchquery.model.query.SpecificationLimit;

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
                                       String temporalBias
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
            rpcVariant.getBias().getBias().name()
        );
    }
}
