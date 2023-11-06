package nu.marginalia.crawl.retreival.fetcher;

import lombok.AllArgsConstructor;
import lombok.ToString;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;

@AllArgsConstructor
@ToString
public class FetchResult {
    public final FetchResultState state;
    public final EdgeUrl url;
    public final EdgeDomain domain;

    public FetchResult(FetchResultState state, EdgeUrl url) {
        this.state = state;
        this.url = url;
        this.domain = url.domain;
    }

    public boolean ok() {
        return state == FetchResultState.OK;
    }
}
