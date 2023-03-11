package nu.marginalia.language.model;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import nu.marginalia.model.idx.WordMetadata;
import nu.marginalia.model.idx.WordFlags;

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
    private final EnumSet<WordFlags> wordFlagsTemplate;

    public KeywordMetadata(EnumSet<WordFlags> flags) {
        this.positionMask = new Object2IntOpenHashMap<>(10_000, 0.7f);
        this.wordsTfIdf =  new Object2IntOpenHashMap<>(10_000, 0.7f);
        this.wordFlagsTemplate = flags;
    }

    public KeywordMetadata() {
        this(EnumSet.noneOf(WordFlags.class));
    }

    public long getMetadataForWord(EnumSet<WordFlags> flagsTemplate, String stemmed) {

        int tfidf = wordsTfIdf.getOrDefault(stemmed, 0);
        EnumSet<WordFlags> flags = flagsTemplate.clone();

        if (tfidf > 100)
            flags.add(WordFlags.TfIdfHigh);

        if (subjectKeywords.contains(stemmed))
            flags.add(WordFlags.Subjects);

        if (namesKeywords.contains(stemmed))
            flags.add(WordFlags.NamesWords);

        if (titleKeywords.contains(stemmed))
            flags.add(WordFlags.Title);

        if (urlKeywords.contains(stemmed))
            flags.add(WordFlags.UrlPath);

        if (domainKeywords.contains(stemmed))
            flags.add(WordFlags.UrlDomain);

        int positions = positionMask.getOrDefault(stemmed, 0);

        return new WordMetadata(tfidf, positions, flags).encode();
    }

    public EnumSet<WordFlags> wordFlagsTemplate() {
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
