package nu.marginalia.api.searchquery.model.results;

import nu.marginalia.api.searchquery.model.results.debug.ResultRankingDetails;
import nu.marginalia.model.DocumentFormat;
import nu.marginalia.model.EdgeUrl;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.List;

public class DecoratedSearchResultItem implements Comparable<DecoratedSearchResultItem> {
    public final SearchResultItem rawIndexResult;

    @NotNull
    public final EdgeUrl url;
    @NotNull
    public final String title;
    @NotNull
    public final String description;
    public final double urlQuality;
    @NotNull
    public final String format;

    /**
     * Document features bitmask, see HtmlFeature
     */
    public final int features;

    @Nullable
    public final Integer pubYear;
    public final long dataHash;
    public final int wordsTotal;
    public final long bestPositions;
    public final double rankingScore;

    public final int resultsFromDomain;

    @Nullable
    public ResultRankingDetails rankingDetails;

    public long documentId() {
        return rawIndexResult.getDocumentId();
    }

    public int domainId() {
        return rawIndexResult.getDomainId();
    }

    public List<SearchResultKeywordScore> keywordScores() {
        return rawIndexResult.getKeywordScores();
    }

    public long rankingId() {
        return rawIndexResult.getRanking();
    }

    public DecoratedSearchResultItem(SearchResultItem rawIndexResult,
                                     @NotNull
                                     EdgeUrl url,
                                     @NotNull
                                     String title,
                                     @NotNull
                                     String description,
                                     double urlQuality,
                                     @NotNull
                                     String format,
                                     int features,
                                     @Nullable
                                     Integer pubYear,
                                     long dataHash,
                                     int wordsTotal,
                                     long bestPositions,
                                     double rankingScore,
                                     int resultsFromDomain,
                                     @Nullable
                                     ResultRankingDetails rankingDetails
    ) {
        this.rawIndexResult = rawIndexResult;
        this.url = url;
        this.title = title;
        this.description = description;
        this.urlQuality = urlQuality;
        this.format = format;
        this.features = features;
        this.pubYear = pubYear;
        this.dataHash = dataHash;
        this.wordsTotal = wordsTotal;
        this.bestPositions = bestPositions;
        this.rankingScore = rankingScore;
        this.resultsFromDomain = resultsFromDomain;
        this.rankingDetails = rankingDetails;
    }

    @Override
    public int compareTo(@NotNull DecoratedSearchResultItem o) {
        int diff = Double.compare(rankingScore, o.rankingScore);

        if (diff == 0)
            diff = Long.compare(documentId(), o.documentId());

        return diff;
    }

    public SearchResultItem getRawIndexResult() {
        return this.rawIndexResult;
    }

    public @NotNull EdgeUrl getUrl() {
        return this.url;
    }

    public @NotNull String getTitle() {
        return this.title;
    }

    public @NotNull String getDescription() {
        return this.description;
    }

    public double getUrlQuality() {
        return this.urlQuality;
    }

    public @NotNull String getFormat() {
        return this.format;
    }

    public int getFeatures() {
        return this.features;
    }

    @Nullable
    public Integer getPubYear() {
        return this.pubYear;
    }

    public long getDataHash() {
        return this.dataHash;
    }

    public int getWordsTotal() {
        return this.wordsTotal;
    }

    public long getBestPositions() {
        return this.bestPositions;
    }

    public double getRankingScore() {
        return this.rankingScore;
    }

    public int getResultsFromDomain() {
        return this.resultsFromDomain;
    }

    @Nullable
    public ResultRankingDetails getRankingDetails() {
        return this.rankingDetails;
    }

    public String toString() {
        return "DecoratedSearchResultItem(rawIndexResult=" + this.getRawIndexResult() + ", url=" + this.getUrl() + ", title=" + this.getTitle() + ", description=" + this.getDescription() + ", urlQuality=" + this.getUrlQuality() + ", format=" + this.getFormat() + ", features=" + this.getFeatures() + ", pubYear=" + this.getPubYear() + ", dataHash=" + this.getDataHash() + ", wordsTotal=" + this.getWordsTotal() + ", bestPositions=" + this.getBestPositions() + ", rankingScore=" + this.getRankingScore() + ", resultsFromDomain=" + this.getResultsFromDomain() + ", rankingDetails=" + this.getRankingDetails() + ")";
    }

    public String getShortFormat() {
        try {
            var df = DocumentFormat.valueOf(format);
            return df.shortFormat;
        }
        catch (IllegalArgumentException e) {
            return DocumentFormat.UNKNOWN.shortFormat;
        }
    }
}
