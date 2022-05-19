package nu.marginalia.wmsa.edge.model.crawl;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.EdgeUrl;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Getter @AllArgsConstructor @ToString
public class EdgeIndexTask {
    public final EdgeDomain domain;
    public final List<Integer> visited = new ArrayList<>();
    public final List<EdgeUrl> urls = new ArrayList<>();
    public final int pass;
    public final int limit;
    public double rank;

    public boolean isEmpty() {
        return domain == null || urls.isEmpty();
    }

    public Stream<EdgeUrl> streamUrls() {
        return urls.stream();
    }

    public int size() {
        return urls.size();
    }
}
