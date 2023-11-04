package nu.marginalia.atags.model;

import nu.marginalia.model.EdgeUrl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DomainLinks {
    private final Map<String, List<Link>> links;

    public DomainLinks()  {
        links = Map.of();
    }

    public DomainLinks(List<LinkWithText> linksForDomain) {
        links = linksForDomain.
                stream()
                .collect(Collectors.groupingBy(LinkWithText::url,
                        Collectors.mapping(LinkWithText::toLink, Collectors.toList())));
    }

    public List<String> getUrls() {
        return new ArrayList<>(links.keySet());
    }

    public List<Link> forUrl(EdgeUrl url) {
        String key = url.domain.toString() + url.path + (url.param == null ? "" : "?" + url.param);
        return links.getOrDefault(key, List.of());
    }

    @Override
    public String toString() {
        return "DomainLinks{" +
                "links=" + links +
                '}';
    }
}
