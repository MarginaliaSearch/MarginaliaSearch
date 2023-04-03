package nu.marginalia.keyword;

import lombok.Builder;
import nu.marginalia.keyword.extractors.*;
import nu.marginalia.model.idx.WordMetadata;
import nu.marginalia.model.idx.WordFlags;

import java.util.EnumSet;

class KeywordMetadata {

    private final KeywordPositionBitmask bitmask;
    private final TitleKeywords titleKeywords;
    private final NameLikeKeywords nameLikeKeywords;
    private final SubjectLikeKeywords subjectLikeKeywords;
    private final UrlKeywords urlKeywords;
    private final WordsTfIdfCounts tfIdfCounts;

    @Builder
    public KeywordMetadata(
            KeywordPositionBitmask bitmask,
            TitleKeywords titleKeywords,
            NameLikeKeywords nameLikeKeywords,
            SubjectLikeKeywords subjectLikeKeywords,
            UrlKeywords urlKeywords,
            WordsTfIdfCounts tfIdfCounts) {

        this.bitmask = bitmask;
        this.titleKeywords = titleKeywords;
        this.nameLikeKeywords = nameLikeKeywords;
        this.subjectLikeKeywords = subjectLikeKeywords;
        this.urlKeywords = urlKeywords;
        this.tfIdfCounts = tfIdfCounts;
    }

    public long getMetadataForWord(String stemmed) {

        int tfidf = tfIdfCounts.getTfIdf(stemmed);
        EnumSet<WordFlags> flags = EnumSet.noneOf(WordFlags.class);

        if (tfidf > 100)
            flags.add(WordFlags.TfIdfHigh);

        if (subjectLikeKeywords.contains(stemmed))
            flags.add(WordFlags.Subjects);

        if (nameLikeKeywords.contains(stemmed))
            flags.add(WordFlags.NamesWords);

        if (titleKeywords.contains(stemmed))
            flags.add(WordFlags.Title);

        if (urlKeywords.containsUrl(stemmed))
            flags.add(WordFlags.UrlPath);

        if (urlKeywords.containsDomain(stemmed))
            flags.add(WordFlags.UrlDomain);

        long positions = bitmask.get(stemmed);

        return new WordMetadata(positions, flags).encode();
    }

}
