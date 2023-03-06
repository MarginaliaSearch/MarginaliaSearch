package nu.marginalia.language.model;

import nu.marginalia.model.idx.WordMetadata;
import nu.marginalia.model.crawl.EdgePageWordFlags;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;

public record KeywordMetadata(HashSet<String> titleKeywords,
                              HashSet<String> subjectKeywords,
                              HashSet<String> namesKeywords,
                              HashMap<String, WordFrequencyData> wordsTfIdf,
                              HashMap<String, Integer> positionMask,
                              EnumSet<EdgePageWordFlags> wordFlagsTemplate
)
{

    public KeywordMetadata(EnumSet<EdgePageWordFlags> flags) {
        this(new HashSet<>(50), new HashSet<>(10), new HashSet<>(50),
                new HashMap<>(15_000),
                new HashMap<>(10_000),
                flags);
    }

    public KeywordMetadata() {
        this(EnumSet.noneOf(EdgePageWordFlags.class));
    }

    private static final WordFrequencyData empty = new WordFrequencyData(0, 0);
    public long getMetadataForWord(EnumSet<EdgePageWordFlags> flagsTemplate, String stemmed) {

        WordFrequencyData tfidf = wordsTfIdf.getOrDefault(stemmed, empty);
        EnumSet<EdgePageWordFlags> flags = flagsTemplate.clone();

        if (subjectKeywords.contains(stemmed))
            flags.add(EdgePageWordFlags.Subjects);

        if (namesKeywords.contains(stemmed))
            flags.add(EdgePageWordFlags.NamesWords);

        if (titleKeywords.contains(stemmed))
            flags.add(EdgePageWordFlags.Title);

        int positions = positionMask.getOrDefault(stemmed, 0);

        return new WordMetadata(tfidf.tfIdfNormalized(), positions, tfidf.count(), flags).encode();
    }

}
