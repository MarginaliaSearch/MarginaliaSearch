package nu.marginalia.util.language.processing.model;

import nu.marginalia.util.language.processing.KeywordCounter;
import nu.marginalia.wmsa.edge.index.model.EdgePageWordFlags;
import nu.marginalia.wmsa.edge.index.model.EdgePageWordMetadata;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;

public record KeywordMetadata(HashSet<String> titleKeywords,
                              HashSet<String> subjectKeywords,
                              HashSet<String> namesKeywords,
                              HashMap<String, KeywordCounter.WordFrequencyData> wordsTfIdf,
                              HashMap<String, Integer> positionMask,
                              EnumSet<EdgePageWordFlags> flagsTemplate,
                              int quality
)
{

    private static final KeywordCounter.WordFrequencyData empty = new KeywordCounter.WordFrequencyData(0, 0);

    public KeywordMetadata(double quality, EnumSet<EdgePageWordFlags> flags) {
        this(new HashSet<>(50), new HashSet<>(10), new HashSet<>(50),
                new HashMap<>(15_000),
                new HashMap<>(10_000),
                flags,
                (int)(-quality));
    }

    public KeywordMetadata(double quality) {
        this(quality, EnumSet.noneOf(EdgePageWordFlags.class));
    }

    public long forWord(EnumSet<EdgePageWordFlags> flagsTemplate, String stemmed) {

        KeywordCounter.WordFrequencyData tfidf = wordsTfIdf.getOrDefault(stemmed, empty);
        EnumSet<EdgePageWordFlags> flags = flagsTemplate.clone();

        if (subjectKeywords.contains(stemmed))
            flags.add(EdgePageWordFlags.Subjects);

        if (namesKeywords.contains(stemmed))
            flags.add(EdgePageWordFlags.NamesWords);

        if (titleKeywords.contains(stemmed))
            flags.add(EdgePageWordFlags.Title);

        int positions = positionMask.getOrDefault(stemmed, 0);

        return new EdgePageWordMetadata(tfidf.tfIdfNormalized(), positions, quality, tfidf.count(), flags).encode();
    }

    public int quality() {
        return -quality;
    }

}
