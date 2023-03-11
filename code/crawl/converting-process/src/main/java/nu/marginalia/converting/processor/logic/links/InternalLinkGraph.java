package nu.marginalia.converting.processor.logic.links;

import nu.marginalia.model.idx.WordFlags;
import nu.marginalia.converting.model.ProcessedDocument;
import nu.marginalia.model.EdgeUrl;

import java.util.*;

public class InternalLinkGraph {
    private final Map<EdgeUrl, Set<EdgeUrl>> internalLinkGraph = new HashMap<>(1000);
    private final Set<EdgeUrl> goodUrls = new HashSet<>(1000);
    private final Map<EdgeUrl, Set<String>> topKeywordsByUrl = new HashMap<>(1000);
    private final Map<EdgeUrl, Set<String>> candidateKeywordsByUrl = new HashMap<>(1000);

    private final Set<EdgeUrl> knownUrls = new HashSet<>(10_000);

    public void accept(ProcessedDocument doc) {
        if (doc.details == null || doc.details.linksInternal == null)
            return;

        goodUrls.add(doc.url);
        internalLinkGraph.put(doc.url, new HashSet<>(doc.details.linksInternal));
        knownUrls.addAll(doc.details.linksInternal);

        List<String> topKeywords = doc.words.getWordsWithAnyFlag(WordFlags.TfIdfHigh.asBit() | WordFlags.Subjects.asBit());

        topKeywordsByUrl.put(doc.url, new HashSet<>(topKeywords));
        candidateKeywordsByUrl.put(doc.url, new HashSet<>(topKeywords));
    }

    public Map<EdgeUrl, Set<EdgeUrl>> trimAndInvert() {
        internalLinkGraph.values().forEach(dest -> dest.retainAll(goodUrls));

        Map<EdgeUrl, Set<EdgeUrl>> inverted = new HashMap<>(goodUrls.size());

        internalLinkGraph.forEach((source, dests) -> {
            dests.forEach(dest -> inverted.computeIfAbsent(dest,
                    d->new HashSet<>(25))
                    .add(source));
        });

        internalLinkGraph.clear();

        return inverted;
    }

    public int numKnownUrls() {
        return knownUrls.size();
    }

    public Set<String> getKeywords(EdgeUrl url) {
        return topKeywordsByUrl.getOrDefault(url, Collections.emptySet());
    }
    public Set<String> getCandidateKeywords(EdgeUrl url) {
        return candidateKeywordsByUrl.getOrDefault(url, Collections.emptySet());
    }
}
