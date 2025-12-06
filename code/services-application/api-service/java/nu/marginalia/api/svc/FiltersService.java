package nu.marginalia.api.svc;

import com.google.inject.Inject;
import nu.marginalia.api.model.ApiLicense;
import nu.marginalia.api.searchquery.model.SearchFilterDefaults;
import nu.marginalia.functions.searchquery.searchfilter.SearchFilterParser;
import nu.marginalia.functions.searchquery.searchfilter.SearchFilterStore;
import nu.marginalia.functions.searchquery.searchfilter.model.SearchFilterSpec;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class FiltersService {
    private final SearchFilterStore filterStore;
    private final int maxFilterCount = 5;
    private final int maxDomainListSize = 100;
    private final int maxTermListSize = 8;

    @Inject
    public FiltersService(SearchFilterStore filterStore) {
        this.filterStore = filterStore;
    }

    public List<String> listFilters(ApiLicense license) {
        return filterStore.getFilterIds(license.key);
    }

    public Optional<String> getFilter(ApiLicense license, String filterId) {
        return filterStore.getFilterDefinition(license.key, filterId);
    }

    public List<String> updateFilter(ApiLicense license,
                             String filterId,
                             String filterDefinition) throws SQLException {

        // Extra protection against an API client somehow getting at the system filters
        if (license.key.equalsIgnoreCase(SearchFilterDefaults.SYSTEM_USER_ID)) {
            return List.of("User not allowed");
        }

        if (filterStore.getFilterIds(license.key).size() >= maxFilterCount) {
            return List.of("Too many filters registered");
        }

        SearchFilterSpec parsedFilter;
        try {
            parsedFilter = filterStore.parseFilter(filterDefinition);
        }
        catch (SearchFilterParser.SearchFilterParserException ex) {
            return List.of("Parsing failed: " + ex.getMessage());
        }

        List<String> problems = new ArrayList<>();

        if (parsedFilter.domainsInclude().size() >= maxDomainListSize) problems.add("Validation failed: Too many domains in domainsInclude, max is " + maxDomainListSize);
        if (parsedFilter.domainsExclude().size() >= maxDomainListSize) problems.add("Validation failed: Too many domains in domainsExclude, max is " + maxDomainListSize);
        if (parsedFilter.domainsPromote().size() >= maxDomainListSize) problems.add("Validation failed: Too many domains in domainsPromote, max is " + maxDomainListSize);

        if (parsedFilter.termsRequire().size() >= maxTermListSize) problems.add("Validation failed: Too many terms in termsRequire, max is " + maxDomainListSize);
        if (parsedFilter.termsExclude().size() >= maxTermListSize) problems.add("Validation failed: Too many terms in termsExclude, max is " + maxDomainListSize);
        if (parsedFilter.termsPromote().size() >= maxTermListSize) problems.add("Validation failed: Too many terms in termsPromote, max is " + maxDomainListSize);

        if (!problems.isEmpty()) {
            return problems;
        }

        filterStore.saveFilter(
                license.key,
                filterId,
                filterDefinition
        );

        return Collections.emptyList();
    }

    public void deleteFilter(ApiLicense license, String filterId) throws SQLException {
        // Extra protection against an API client somehow getting at the system filters
        if (license.key.equalsIgnoreCase(SearchFilterDefaults.SYSTEM_USER_ID)) {
            throw new RuntimeException("Not allowed");
        }

        filterStore.deleteFilter(
                license.key,
                filterId
        );
    }


}
