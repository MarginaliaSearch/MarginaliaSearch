package nu.marginalia.atags.model;

import nu.marginalia.model.EdgeUrl;

import java.util.ArrayList;
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

    /** Get all urls in this domain. */
    public List<EdgeUrl> getUrls(String schema) {
        List<EdgeUrl> ret = new ArrayList<>(links.size());

        for (var link : links.keySet()) {
            EdgeUrl.parse(schema + "://" + link).ifPresent(ret::add);
        }

        return ret;
    }

    /** Returns the links to the given url. */
    public List<Link> forUrl(EdgeUrl url) {
        String key = url.domain.toString() + url.path + (url.param == null ? "" : "?" + url.param);
        return links.getOrDefault(key, List.of());
    }

    /** Returns the number of links to the given url. */
    public int countForUrl(EdgeUrl url) {
        String key = url.domain.toString() + url.path + (url.param == null ? "" : "?" + url.param);
        return links.getOrDefault(key, List.of()).size();
    }

    @Override
    public String toString() {
        return "DomainLinks{" +
                "links=" + links +
                '}';
    }
}
