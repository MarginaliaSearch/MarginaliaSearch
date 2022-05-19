package nu.marginalia.wmsa.memex;

import nu.marginalia.wmsa.memex.model.MemexLink;
import nu.marginalia.wmsa.memex.model.MemexNodeUrl;

import java.util.*;
import java.util.stream.Collectors;

public class MemexLinks {
    private Map<MemexNodeUrl, List<MemexLink>> backLinks = new HashMap<>();
    private final Map<MemexNodeUrl, Set<MemexLink>> links = new HashMap<>();

    public void updateBacklinks() {
        backLinks.clear();
        backLinks = links.values().stream()
                .flatMap(Set::stream)
                .collect(Collectors.groupingBy(MemexLink::getDest));
    }

    public Set<MemexNodeUrl> getNeighbors(MemexNodeUrl url) {
        final Set<MemexNodeUrl> neighbors = new HashSet<>();

        links.getOrDefault(url, Collections.emptySet()).stream().map(MemexLink::getDest)
                .forEach(neighbors::add);
        backLinks.getOrDefault(url, Collections.emptyList()).stream()
                .map(MemexLink::getSrc)
                .forEach(neighbors::add);

        return neighbors;
    }

    public void setOutlinks(MemexNodeUrl url, TreeSet<MemexLink> linksForNode) {
        links.put(url, linksForNode);
        updateBacklinks();
    }

    public List<MemexLink> getBacklinks(MemexNodeUrl... urls) {
        return Arrays.stream(urls)
                .map(backLinks::get)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .sorted(Comparator.comparing(MemexLink::getSrc))
                .collect(Collectors.toList());
    }

    public Set<MemexLink> getOutlinks(MemexNodeUrl url) {
        return links.getOrDefault(url, Collections.emptySet());
    }

    public void remove(MemexNodeUrl url) {
        links.remove(url);
        updateBacklinks();
    }
}
