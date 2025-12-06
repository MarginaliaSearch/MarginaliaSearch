package nu.marginalia.api.searchquery.model.query;

import nu.marginalia.api.searchquery.RpcIndexQuery;

import java.util.List;

public class ProcessedQuery {
    public final RpcIndexQuery indexQuery;
    public final List<String> searchTermsHuman;
    public final String domain;
    public final String langIsoCode;

    @Override
    public String toString() {
        return "ProcessedQuery{" +
                "indexQuery=" + indexQuery +
                ", searchTermsHuman=" + searchTermsHuman +
                ", domain='" + domain + '\'' +
                ", langIsoCode='" + langIsoCode + '\'' +
                '}';
    }

    public ProcessedQuery(RpcIndexQuery indexQuery,
                          List<String> searchTermsHuman,
                          String domain,
                          String langIsoCode) {
        this.indexQuery = indexQuery;
        this.searchTermsHuman = searchTermsHuman;
        this.domain = domain;
        this.langIsoCode = langIsoCode;
    }

}
