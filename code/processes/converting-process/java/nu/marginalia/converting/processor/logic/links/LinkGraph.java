package nu.marginalia.converting.processor.logic.links;

import nu.marginalia.converting.model.ProcessedDocument;
import nu.marginalia.model.EdgeUrl;

import java.util.*;
import java.util.function.BiConsumer;

public class LinkGraph {
    private final Map<EdgeUrl, Set<EdgeUrl>> graph = new HashMap<>(1000);

    public LinkGraph() {}

    public void add(ProcessedDocument doc) {
        if (doc.details == null || doc.details.linksInternal == null)
            return;

        add(doc.url, doc.details.linksInternal);
    }

    public void add(EdgeUrl source, EdgeUrl dest) {
        graph.computeIfAbsent(source, s -> new HashSet<>()).add(dest);
    }
    public void add(EdgeUrl source, Collection<EdgeUrl> dest) {
        graph.computeIfAbsent(source, s -> new HashSet<>()).addAll(dest);
    }

    public LinkGraph invert() {
        var invertedGraph = new LinkGraph();

        graph.forEach((source, dests) -> {
            for (var dest : dests) {

                if (!graph.containsKey(dest))
                    continue;

                invertedGraph.add(dest, source);
            }
        });

        return invertedGraph;
    }

    public int size() {
        return graph.size();
    }

    public int numLinks(EdgeUrl url) {
        return graph.getOrDefault(url, Collections.emptySet()).size();
    }

    public void forEach(BiConsumer<EdgeUrl, Set<EdgeUrl>> consumer) {
        graph.forEach(consumer);
    }
}
