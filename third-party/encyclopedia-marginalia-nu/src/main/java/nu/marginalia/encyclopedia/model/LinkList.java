package nu.marginalia.encyclopedia.model;

import java.util.List;

public record LinkList(List<Link> links) {
    public LinkList(Link... links) {
        this(List.of(links));
    }

    public int size() {
        return links.size();
    }
}
