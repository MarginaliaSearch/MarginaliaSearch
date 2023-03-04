package nu.marginalia.converting.processor;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import nu.marginalia.model.crawl.EdgePageWordFlags;
import nu.marginalia.converting.model.ProcessedDocument;
import nu.marginalia.converting.model.ProcessedDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.converting.processor.logic.CommonKeywordExtractor;
import nu.marginalia.converting.processor.logic.InternalLinkGraph;

import javax.inject.Singleton;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class SiteWords {

    private static final CommonKeywordExtractor commonKeywordExtractor = new CommonKeywordExtractor();

    public void flagAdjacentWords(InternalLinkGraph internalLinkGraph, ProcessedDomain processedDomain) {
        Map<EdgeUrl, Set<String>> linkedKeywords = getAdjacentWords(internalLinkGraph);

        for (var doc : processedDomain.documents) {
            applyKeywordsToDoc(doc, EdgePageWordFlags.SiteAdjacent, linkedKeywords.get(doc.url));
        }

    }

    public void flagCommonSiteWords(ProcessedDomain processedDomain) {
        Set<String> commonSiteWords = new HashSet<>(10);

        commonSiteWords.addAll(commonKeywordExtractor.getCommonSiteWords(processedDomain,
                EdgePageWordFlags.Subjects));

        commonSiteWords.addAll(commonKeywordExtractor.getCommonSiteWords(processedDomain,
                EdgePageWordFlags.Title));

        if (commonSiteWords.isEmpty()) {
            return;
        }

        for (var doc : processedDomain.documents) {
            applyKeywordsToDoc(doc, EdgePageWordFlags.Site, commonSiteWords);
        }
    }

    private Map<EdgeUrl, Set<String>> getAdjacentWords(InternalLinkGraph internalLinkGraph) {

        final Map<EdgeUrl, Set<EdgeUrl>> invertedGraph = internalLinkGraph.trimAndInvert();
        final Map<EdgeUrl, Set<String>> linkedKeywords = new HashMap<>(100);

        invertedGraph.forEach((url, linkingUrls) -> {
            Object2IntOpenHashMap<String> keywords = new Object2IntOpenHashMap<>(100);

            for (var linkingUrl : linkingUrls) {
                for (var keyword : internalLinkGraph.getKeywords(linkingUrl)) {
                    keywords.mergeInt(keyword, 1, Integer::sum);
                }
            }

            var words = keywords.object2IntEntrySet().stream()
                    .filter(e -> e.getIntValue() > 3)
                    .map(Map.Entry::getKey)
                    .filter(internalLinkGraph.getCandidateKeywords(url)::contains)
                    .collect(Collectors.toSet());
            if (!words.isEmpty()) {
                linkedKeywords.put(url, words);
            }
        });

        return linkedKeywords;
    }

    private void applyKeywordsToDoc(ProcessedDocument doc, EdgePageWordFlags flag, Set<String> words) {
        if (doc.words != null && words != null) {
            doc.words.setFlagOnMetadataForWords(flag, words);
        }
    }


}
