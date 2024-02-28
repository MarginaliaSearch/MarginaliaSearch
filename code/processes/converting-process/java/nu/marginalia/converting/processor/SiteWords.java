package nu.marginalia.converting.processor;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import nu.marginalia.converting.processor.logic.links.LinkGraph;
import nu.marginalia.model.idx.WordFlags;
import nu.marginalia.converting.model.ProcessedDocument;
import nu.marginalia.converting.model.ProcessedDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.converting.processor.logic.links.CommonKeywordExtractor;
import nu.marginalia.converting.processor.logic.links.TopKeywords;

import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class SiteWords {

    private static final CommonKeywordExtractor commonKeywordExtractor = new CommonKeywordExtractor();

    public void flagAdjacentWords(TopKeywords topKeywords, LinkGraph invertedLinkGraph, ProcessedDomain processedDomain) {
        Map<EdgeUrl, Set<String>> linkedKeywords = getAdjacentWords(topKeywords, invertedLinkGraph);

        for (var doc : processedDomain.documents) {
            applyKeywordsToDoc(doc, WordFlags.SiteAdjacent, linkedKeywords.get(doc.url));
        }

    }

    public void flagCommonSiteWords(ProcessedDomain processedDomain) {
        Set<String> commonSiteWords = new HashSet<>(10);

        commonSiteWords.addAll(commonKeywordExtractor.getCommonSiteWords(processedDomain,
                WordFlags.Subjects));

        commonSiteWords.addAll(commonKeywordExtractor.getCommonSiteWords(processedDomain,
                WordFlags.NamesWords));

        if (commonSiteWords.isEmpty()) return;

        for (var doc : processedDomain.documents) {
            applyKeywordsToDoc(doc, WordFlags.Site, commonSiteWords);
        }
    }

    private Map<EdgeUrl, Set<String>> getAdjacentWords(TopKeywords topKeywords, LinkGraph invertedLinkGraph) {

        final Map<EdgeUrl, Set<String>> linkedKeywords = new HashMap<>(100);

        invertedLinkGraph.forEach((url, linkingUrls) -> {
            Object2IntOpenHashMap<String> keywords = new Object2IntOpenHashMap<>(100);

            for (var linkingUrl : linkingUrls) {
                for (var keyword : topKeywords.getKeywords(linkingUrl)) {
                    keywords.mergeInt(keyword, 1, Integer::sum);
                }
            }

            var words = keywords.object2IntEntrySet().stream()
                    .filter(e -> e.getIntValue() > 3)
                    .map(Map.Entry::getKey)
                    .filter(topKeywords.getKeywords(url)::contains)
                    .collect(Collectors.toSet());

            if (words.isEmpty()) return;

            linkedKeywords.put(url, words);
        });

        return linkedKeywords;
    }

    private void applyKeywordsToDoc(ProcessedDocument doc, WordFlags flag, Set<String> words) {

        if (doc.words == null) return;
        if (words == null) return;

        doc.words.setFlagOnMetadataForWords(flag, words);
    }


}
