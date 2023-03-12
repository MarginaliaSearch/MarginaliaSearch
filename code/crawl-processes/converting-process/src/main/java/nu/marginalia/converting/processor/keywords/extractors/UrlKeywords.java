package nu.marginalia.converting.processor.keywords.extractors;

import ca.rmen.porterstemmer.PorterStemmer;
import nu.marginalia.model.EdgeUrl;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class UrlKeywords {
    private final PorterStemmer ps = new PorterStemmer();

    public Set<String> getUrlKeywords(EdgeUrl url) {
        String path = url.path;

        return Arrays.stream(path.split("[^a-z0-9A-Z]+"))
                .map(ps::stemWord)
                .collect(Collectors.toSet());
    }

    public Set<String> getDomainKeywords(EdgeUrl url) {
        return Arrays.stream(url.domain.domain.split("[^a-z0-9A-Z]+"))
                .filter(s -> s.length() > 3)
                .map(ps::stemWord)
                .collect(Collectors.toSet());
    }
}
