package nu.marginalia.crawl.retreival.fetcher;

import lombok.AllArgsConstructor;
import lombok.ToString;
import nu.marginalia.model.EdgeDomain;

@AllArgsConstructor
@ToString
public class FetchResult {
    public final FetchResultState state;
    public final EdgeDomain domain;

    public boolean ok() {
        return state == FetchResultState.OK;
    }
}
