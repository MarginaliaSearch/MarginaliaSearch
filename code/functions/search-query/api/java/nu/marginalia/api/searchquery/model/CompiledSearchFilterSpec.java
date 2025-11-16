package nu.marginalia.api.searchquery.model;

import it.unimi.dsi.fastutil.floats.FloatList;
import it.unimi.dsi.fastutil.ints.IntList;
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
                                       SpecificationLimit quality)
{

}
