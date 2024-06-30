package nu.marginalia.keyword;

import lombok.Builder;
import nu.marginalia.keyword.extractors.*;
import nu.marginalia.model.idx.WordFlags;

class KeywordMetadata {

    private final TitleKeywords titleKeywords;
    private final NameLikeKeywords nameLikeKeywords;
    private final SubjectLikeKeywords subjectLikeKeywords;
    private final UrlKeywords urlKeywords;
    private final WordsTfIdfCounts tfIdfCounts;

    @Builder
    public KeywordMetadata(
            TitleKeywords titleKeywords,
            NameLikeKeywords nameLikeKeywords,
            SubjectLikeKeywords subjectLikeKeywords,
            UrlKeywords urlKeywords,
            WordsTfIdfCounts tfIdfCounts)
    {
        this.titleKeywords = titleKeywords;
        this.nameLikeKeywords = nameLikeKeywords;
        this.subjectLikeKeywords = subjectLikeKeywords;
        this.urlKeywords = urlKeywords;
        this.tfIdfCounts = tfIdfCounts;
    }

    public long getMetadataForWord(String stemmed) {

        int tfidf = tfIdfCounts.getTfIdf(stemmed);
        long flags = 0;

        if (tfidf > 100) {
            flags |= WordFlags.TfIdfHigh.asBit();
        }

        if (subjectLikeKeywords.contains(stemmed)) {
            flags |= WordFlags.Subjects.asBit();
        }

        if (nameLikeKeywords.contains(stemmed)) {
            flags |= WordFlags.NamesWords.asBit();
        }

        if (titleKeywords.contains(stemmed)) {
            flags |= WordFlags.Title.asBit();
        }

        if (urlKeywords.containsUrl(stemmed)) {
            flags |= WordFlags.UrlPath.asBit();
        }

        if (urlKeywords.containsDomain(stemmed)) {
            flags |= WordFlags.UrlDomain.asBit();
        }

        return flags;
    }

}
