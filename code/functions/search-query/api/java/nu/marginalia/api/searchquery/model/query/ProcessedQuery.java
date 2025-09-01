package nu.marginalia.api.searchquery.model.query;

import java.util.List;

public class ProcessedQuery {
    public final SearchSpecification specs;
    public final List<String> searchTermsHuman;
    public final String domain;
    public final String langIsoCode;

    public ProcessedQuery(SearchSpecification specs,
                          List<String> searchTermsHuman,
                          String domain,
                          String langIsoCode) {
        this.specs = specs;
        this.searchTermsHuman = searchTermsHuman;
        this.domain = domain;
        this.langIsoCode = langIsoCode;
    }

    public ProcessedQuery(SearchSpecification justSpecs) {
        this(justSpecs, List.of(), null, "en");
    }
}
