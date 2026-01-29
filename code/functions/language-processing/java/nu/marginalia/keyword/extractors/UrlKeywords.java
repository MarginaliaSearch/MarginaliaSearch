package nu.marginalia.keyword.extractors;

import ca.rmen.porterstemmer.PorterStemmer;
import nu.marginalia.language.model.DocumentSentence;
import nu.marginalia.language.sentence.tag.HtmlTag;
import nu.marginalia.model.EdgeUrl;

import java.util.*;
import java.util.regex.Pattern;

/** Extract keywords from the URL */
public class UrlKeywords {
    private static final PorterStemmer ps = new PorterStemmer();

    private static final Pattern splitPattern = Pattern.compile("^[a-z0-9A-Z]+");

    private final Set<String> urlKeywords = new HashSet<>();
    private final Set<String> domainKeywords = new HashSet<>();

    private final DocumentSentence searchableKeywordsSentence;

    public UrlKeywords(EdgeUrl url) {
        String path = url.path;

        List<String> searchableKeywordsLC = new ArrayList<>();
        List<String> searchableKeywordsStemmed = new ArrayList<>();

        String[] domainParts = splitPattern.split(url.domain.toString());
        for (int i = 0; i < domainParts.length; i++) {
            String part = domainParts[i];

            if (i == 0 && part.equals("www"))
                continue;

            if (i == domainParts.length-1) {
                if (part.equals("com") || part.equals("net") || part.equals("org")) {
                    searchableKeywordsLC.add("");
                    searchableKeywordsStemmed.add("");
                    continue;
                }
            }

            String stemmed = ps.stemWord(part);

            domainKeywords.add(ps.stemWord(part));

            searchableKeywordsStemmed.add(stemmed);
            searchableKeywordsLC.add(part.toLowerCase());
        }

        String[] urlParts = splitPattern.split(url.path);
        for (int i = 0; i < urlParts.length; i++) {
            String part = urlParts[i];
            String stemmed = ps.stemWord(part);

            urlKeywords.add(stemmed);

            searchableKeywordsLC.add(part.toLowerCase());
            searchableKeywordsStemmed.add(stemmed);
        }

        searchableKeywordsSentence = new DocumentSentence(
                new BitSet(searchableKeywordsLC.size()),
                searchableKeywordsLC.toArray(String[]::new),
                new long[0],
                searchableKeywordsStemmed.toArray(String[]::new),
                EnumSet.of(HtmlTag.DOC_URL),
                new BitSet(searchableKeywordsLC.size()),
                new BitSet(searchableKeywordsLC.size()),
                new BitSet(searchableKeywordsLC.size())
        );

    }

    public boolean containsUrl(String stemmed) {
        return urlKeywords.contains(stemmed);
    }
    public boolean containsDomain(String stemmed) {
        return domainKeywords.contains(stemmed);
    }

    public DocumentSentence searchableKeywords() {
        return searchableKeywordsSentence;
    }
}
