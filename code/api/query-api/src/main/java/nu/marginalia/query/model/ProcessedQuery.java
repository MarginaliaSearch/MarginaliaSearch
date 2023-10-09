package nu.marginalia.query.model;

import nu.marginalia.index.client.model.query.SearchSpecification;

import java.util.*;

public class ProcessedQuery {
    public final SearchSpecification specs;
    public final List<String> searchTermsHuman;
    public final String domain;

    public ProcessedQuery(SearchSpecification specs, List<String> searchTermsHuman, String domain) {
        this.specs = specs;
        this.searchTermsHuman = searchTermsHuman;
        this.domain = domain;
    }

    public ProcessedQuery(SearchSpecification justSpecs) {
        this(justSpecs, List.of(), null);
    }
}
