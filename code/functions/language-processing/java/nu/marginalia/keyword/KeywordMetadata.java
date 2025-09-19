package nu.marginalia.keyword;

import nu.marginalia.keyword.extractors.NameLikeKeywords;
import nu.marginalia.keyword.extractors.SubjectLikeKeywords;
import nu.marginalia.keyword.extractors.TitleKeywords;
import nu.marginalia.keyword.extractors.UrlKeywords;
import nu.marginalia.model.idx.WordFlags;

import javax.annotation.Nullable;

public class KeywordMetadata {

    @Nullable
    private final TitleKeywords titleKeywords;
    @Nullable
    private final NameLikeKeywords nameLikeKeywords;
    @Nullable
    private final SubjectLikeKeywords subjectLikeKeywords;
    @Nullable
    private final UrlKeywords urlKeywords;

    public KeywordMetadata(
            @Nullable TitleKeywords titleKeywords,
            @Nullable NameLikeKeywords nameLikeKeywords,
            @Nullable SubjectLikeKeywords subjectLikeKeywords,
            @Nullable UrlKeywords urlKeywords) {
        this.titleKeywords = titleKeywords;
        this.nameLikeKeywords = nameLikeKeywords;
        this.subjectLikeKeywords = subjectLikeKeywords;
        this.urlKeywords = urlKeywords;
    }

    public static KeywordMetadataBuilder builder() {
        return new KeywordMetadataBuilder();
    }

    public byte getMetadataForWord(String stemmed) {

        byte flags = 0;

        if (subjectLikeKeywords != null && subjectLikeKeywords.contains(stemmed)) {
            flags |= WordFlags.Subjects.asBit();
        }

        if (nameLikeKeywords != null && nameLikeKeywords.contains(stemmed)) {
            flags |= WordFlags.NamesWords.asBit();
        }

        if (titleKeywords != null && titleKeywords.contains(stemmed)) {
            flags |= WordFlags.Title.asBit();
        }

        if (urlKeywords != null && urlKeywords.containsUrl(stemmed)) {
            flags |= WordFlags.UrlPath.asBit();
        }

        if (urlKeywords != null && urlKeywords.containsDomain(stemmed)) {
            flags |= WordFlags.UrlDomain.asBit();
        }

        return flags;
    }

    public static class KeywordMetadataBuilder {
        private TitleKeywords titleKeywords;
        private NameLikeKeywords nameLikeKeywords;
        private SubjectLikeKeywords subjectLikeKeywords;
        private UrlKeywords urlKeywords;

        KeywordMetadataBuilder() {
        }

        public KeywordMetadataBuilder titleKeywords(TitleKeywords titleKeywords) {
            this.titleKeywords = titleKeywords;
            return this;
        }

        public KeywordMetadataBuilder nameLikeKeywords(NameLikeKeywords nameLikeKeywords) {
            this.nameLikeKeywords = nameLikeKeywords;
            return this;
        }

        public KeywordMetadataBuilder subjectLikeKeywords(SubjectLikeKeywords subjectLikeKeywords) {
            this.subjectLikeKeywords = subjectLikeKeywords;
            return this;
        }

        public KeywordMetadataBuilder urlKeywords(UrlKeywords urlKeywords) {
            this.urlKeywords = urlKeywords;
            return this;
        }

        public KeywordMetadata build() {
            return new KeywordMetadata(this.titleKeywords, this.nameLikeKeywords, this.subjectLikeKeywords, this.urlKeywords);
        }

        public String toString() {
            return "KeywordMetadata.KeywordMetadataBuilder(titleKeywords=" + this.titleKeywords + ", nameLikeKeywords=" + this.nameLikeKeywords + ", subjectLikeKeywords=" + this.subjectLikeKeywords + ", urlKeywords=" + this.urlKeywords + ")";
        }
    }
}
