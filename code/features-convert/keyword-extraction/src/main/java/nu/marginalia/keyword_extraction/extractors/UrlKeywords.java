package nu.marginalia.keyword_extraction.extractors;

import ca.rmen.porterstemmer.PorterStemmer;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** Extract keywords from the URL */
public class UrlKeywords {
    private static final PorterStemmer ps = new PorterStemmer();

    private final Set<String> urlKeywords;
    private final Set<String> domainKeywords;

    public UrlKeywords(EdgeUrl url) {
        String path = url.path;


        urlKeywords = Arrays.stream(path.split("[^a-z0-9A-Z]+"))
                .map(ps::stemWord)
                .collect(Collectors.toSet());

        domainKeywords = Arrays.stream(url.domain.toString().split("[^a-z0-9A-Z]+"))
                .filter(s -> s.length() > 3)
                .map(ps::stemWord)
                .collect(Collectors.toSet());
    }

    public boolean containsUrl(String stemmed) {
        return urlKeywords.contains(stemmed);
    }


    public boolean containsDomain(String stemmed) {
        return domainKeywords.contains(stemmed);
    }
}
