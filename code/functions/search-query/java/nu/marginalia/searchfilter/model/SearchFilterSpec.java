package nu.marginalia.searchfilter.model;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.floats.FloatList;
import it.unimi.dsi.fastutil.floats.FloatLists;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import nu.marginalia.api.searchquery.model.CompiledSearchFilterSpec;
import nu.marginalia.api.searchquery.model.query.SpecificationLimit;
import nu.marginalia.db.DbDomainQueries;
import nu.marginalia.model.EdgeDomain;

import java.util.*;

public record SearchFilterSpec(String userId,
                               String identifier,
                               List<String> domainsInclude,
                               List<String> domainsExclude,
                               List<Map.Entry<String, Float>> domainsPromote,
                               String searchSetIdentifier,
                               List<String> termsRequire,
                               List<String> termsExclude,
                               List<Map.Entry<String, Float>> termsPromote,
                               SpecificationLimit year,
                               SpecificationLimit size,
                               SpecificationLimit quality,
                               SpecificationLimit rank,
                               String temporalBias
                               )
{

    public CompiledSearchFilterSpec compile(DbDomainQueries domainQueries) {
        IntList domainIdsRequire = new IntArrayList();
        IntList domainIdsExclude = new IntArrayList();
        IntList domainIdsPromote = new IntArrayList();
        FloatList domainIdsPromoteAmount = new FloatArrayList();

        List<String> newTermsRequire = new ArrayList<>(termsRequire);
        List<String> newTermsExclude = new ArrayList<>(termsExclude);
        List<String> newTermsPromote = new ArrayList<>(termsPromote.size());
        FloatList newTermsPromoteAmount = new FloatArrayList();

        for (var entry : termsPromote) {
            newTermsPromote.add(entry.getKey());
            newTermsPromoteAmount.add(entry.getValue().floatValue());
        }

        remapDomainIds(domainIdsRequire, newTermsRequire, domainsInclude, domainQueries);
        remapDomainIds(domainIdsExclude, newTermsExclude, domainsExclude, domainQueries);
        remapDomainIds(domainIdsPromote, domainIdsPromoteAmount, newTermsPromote, newTermsPromoteAmount, domainsPromote, domainQueries);

        return new CompiledSearchFilterSpec(
                userId,
                identifier,
                IntLists.unmodifiable(domainIdsRequire),
                IntLists.unmodifiable(domainIdsExclude),
                IntLists.unmodifiable(domainIdsPromote),
                FloatLists.unmodifiable(domainIdsPromoteAmount),
                searchSetIdentifier,
                Collections.unmodifiableList(newTermsRequire),
                Collections.unmodifiableList(newTermsExclude),
                Collections.unmodifiableList(newTermsPromote),
                FloatLists.unmodifiable(newTermsPromoteAmount),
                year,
                size,
                quality,
                rank,
                temporalBias);
    }

    private static void remapDomainIds(IntList destDomainIds,
                                       List<String> destTerms,
                                       List<String> sourceDomainNames,
                                       DbDomainQueries queries)
    {
        for (String domain : sourceDomainNames) {
            if (domain.startsWith("*.")) {
                destTerms.add(domain.substring("*.".length()));
            }
            else {
                EdgeDomain ed = new EdgeDomain(domain);
                OptionalInt domainId = queries.tryGetDomainId(ed);
                if (domainId.isPresent()) {
                    destDomainIds.add(domainId.getAsInt());
                }
            }
        }
    }

    private static void remapDomainIds(IntList destDomainIds,
                                       FloatList destPromoteDomainAmounts,
                                       List<String> destTerms,
                                       FloatList destPromoteTermAmounts,
                                       List<Map.Entry<String, Float>> sourceDomainNames,
                                       DbDomainQueries queries)
    {
        for (Map.Entry<String, Float> item : sourceDomainNames) {
            String domain = item.getKey();

            if (domain.startsWith("*.")) {
                destTerms.add(domain.substring("*.".length()));
                destPromoteTermAmounts.add(item.getValue().floatValue());
            }
            else {
                EdgeDomain ed = new EdgeDomain(domain);
                OptionalInt domainId = queries.tryGetDomainId(ed);

                if (domainId.isEmpty()) continue;

                destDomainIds.add(domainId.getAsInt());
                destPromoteDomainAmounts.add(item.getValue().floatValue());
            }
        }
    }

}
