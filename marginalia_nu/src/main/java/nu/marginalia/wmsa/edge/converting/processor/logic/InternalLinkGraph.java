package nu.marginalia.wmsa.edge.converting.processor.logic;

import nu.marginalia.wmsa.edge.converting.model.ProcessedDocument;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.model.EdgeUrl;

import java.util.*;

public class InternalLinkGraph {
    private final Map<EdgeUrl, Set<EdgeUrl>> internalLinkGraph = new HashMap<>(1000);
    private final Set<EdgeUrl> goodUrls = new HashSet<>(1000);
    private final Map<EdgeUrl, Set<String>> topKeywordsByUrl = new HashMap<>(1000);
    private final Map<EdgeUrl, Set<String>> candidateKeywordsByUrl = new HashMap<>(1000);

    public void accept(ProcessedDocument doc) {
        if (doc.details == null || doc.details.linksInternal == null)
            return;

        goodUrls.add(doc.url);
        internalLinkGraph.put(doc.url, new HashSet<>(doc.details.linksInternal));

        Set<String> topKeywords = new HashSet<>(doc.words.get(IndexBlock.Tfidf_High).words);
        topKeywords.addAll(doc.words.get(IndexBlock.Subjects).words);
        topKeywordsByUrl.put(doc.url, topKeywords);

        Set<String> candidateKeywords = new HashSet<>(topKeywords);
        candidateKeywords.addAll(doc.words.get(IndexBlock.Tfidf_High).words);
        candidateKeywords.addAll(doc.words.get(IndexBlock.Subjects).words);
        candidateKeywordsByUrl.put(doc.url, candidateKeywords);
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

    public Set<String> getKeywords(EdgeUrl url) {
        return topKeywordsByUrl.getOrDefault(url, Collections.emptySet());
    }
    public Set<String> getCandidateKeywords(EdgeUrl url) {
        return candidateKeywordsByUrl.getOrDefault(url, Collections.emptySet());
    }
}
