package nu.marginalia.converting.processor.logic.links;

import nu.marginalia.model.idx.WordFlags;
import nu.marginalia.converting.model.ProcessedDocument;
import nu.marginalia.model.EdgeUrl;

import java.util.*;

public class TopKeywords {
    private final Map<EdgeUrl, Set<String>> topKeywordsByUrl = new HashMap<>(1000);

    public void accept(ProcessedDocument doc) {
        if (doc.details == null || doc.details.linksInternal == null)
            return;

        List<String> topKeywords = doc.words.getWordsWithAnyFlag(WordFlags.TfIdfHigh.asBit() | WordFlags.Subjects.asBit());

        topKeywordsByUrl.put(doc.url, new HashSet<>(topKeywords));
    }

    public Set<String> getKeywords(EdgeUrl url) {
        return topKeywordsByUrl.getOrDefault(url, Collections.emptySet());
    }

}
