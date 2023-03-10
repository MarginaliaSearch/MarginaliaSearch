package nu.marginalia.language.model;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import nu.marginalia.model.idx.WordMetadata;
import nu.marginalia.model.crawl.EdgePageWordFlags;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Objects;

public final class KeywordMetadata {

    private static final WordFrequencyData empty = new WordFrequencyData(0);
    public final HashSet<String> titleKeywords = new HashSet<>(50);
    public final HashSet<String> subjectKeywords = new HashSet<>(10);
    public final HashSet<String> namesKeywords = new HashSet<>(50);

    public final HashSet<String> urlKeywords = new HashSet<>(10);

    public final HashSet<String> domainKeywords = new HashSet<>(10);

    public final Object2IntOpenHashMap<String> wordsTfIdf;
    public final Object2IntOpenHashMap<String> positionMask;
    private final EnumSet<EdgePageWordFlags> wordFlagsTemplate;

    public KeywordMetadata(EnumSet<EdgePageWordFlags> flags) {
        this.positionMask = new Object2IntOpenHashMap<>(10_000, 0.7f);
        this.wordsTfIdf =  new Object2IntOpenHashMap<>(10_000, 0.7f);
        this.wordFlagsTemplate = flags;
    }

    public KeywordMetadata() {
        this(EnumSet.noneOf(EdgePageWordFlags.class));
    }

    public long getMetadataForWord(EnumSet<EdgePageWordFlags> flagsTemplate, String stemmed) {

        int tfidf = wordsTfIdf.getOrDefault(stemmed, 0);
        EnumSet<EdgePageWordFlags> flags = flagsTemplate.clone();

        if (tfidf > 100)
            flags.add(EdgePageWordFlags.TfIdfHigh);

        if (subjectKeywords.contains(stemmed))
            flags.add(EdgePageWordFlags.Subjects);

        if (namesKeywords.contains(stemmed))
            flags.add(EdgePageWordFlags.NamesWords);

        if (titleKeywords.contains(stemmed))
            flags.add(EdgePageWordFlags.Title);

        if (urlKeywords.contains(stemmed))
            flags.add(EdgePageWordFlags.UrlPath);

        if (domainKeywords.contains(stemmed))
            flags.add(EdgePageWordFlags.UrlDomain);

        int positions = positionMask.getOrDefault(stemmed, 0);

        return new WordMetadata(tfidf, positions, flags).encode();
    }

    public EnumSet<EdgePageWordFlags> wordFlagsTemplate() {
        return wordFlagsTemplate;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (KeywordMetadata) obj;
        return Objects.equals(this.titleKeywords, that.titleKeywords) &&
                Objects.equals(this.subjectKeywords, that.subjectKeywords) &&
                Objects.equals(this.namesKeywords, that.namesKeywords) &&
                Objects.equals(this.wordsTfIdf, that.wordsTfIdf) &&
                Objects.equals(this.positionMask, that.positionMask) &&
                Objects.equals(this.wordFlagsTemplate, that.wordFlagsTemplate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(titleKeywords, subjectKeywords, namesKeywords, wordsTfIdf, positionMask, wordFlagsTemplate);
    }

    @Override
    public String toString() {
        return "KeywordMetadata[" +
                "titleKeywords=" + titleKeywords + ", " +
                "subjectKeywords=" + subjectKeywords + ", " +
                "namesKeywords=" + namesKeywords + ", " +
                "wordsTfIdf=" + wordsTfIdf + ", " +
                "positionMask=" + positionMask + ", " +
                "wordFlagsTemplate=" + wordFlagsTemplate + ']';
    }


}
