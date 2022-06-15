package nu.marginalia.wmsa.edge.model.search;

import lombok.*;
import nu.marginalia.wmsa.edge.converting.processor.logic.HtmlFeature;
import nu.marginalia.wmsa.edge.model.crawl.EdgeDomainIndexingState;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import nu.marginalia.wmsa.edge.search.model.EdgeSearchRankingSymbols;

import java.util.Objects;

@AllArgsConstructor @NoArgsConstructor @With @Getter @ToString
public class EdgeUrlDetails {
    public int id;
    public EdgeUrl url;
    public String title;
    public String description;

    public double urlQuality;

    public int words;
    public String format;
    public int features;



    public String ip; // BROKEN
    public EdgeDomainIndexingState domainState;


    public int dataHash;

    public EdgePageScoreAdjustment urlQualityAdjustment;
    public long rankingId;
    public double termScore;
    public int queryLength;

    public long rankingIdAdjustment() {
        int penalty = 0;

        if (words < 500) {
            penalty -= 1;
        }
        if (urlQuality < -10) {
            penalty -= 1;
        }
        if (isSpecialDomain()) {
            penalty -= 1;
        }
        return penalty; //(int)(Math.log(1+rankingId) / Math.log(100))-1-penalty;
    }

    public String getFormat() {
        if (null == format) {
            return "?";
        }
        switch (format) {
            case "HTML123":
                return "HTML 1-3";
            case "HTML4":
                return "HTML 4";
            case "XHTML":
                return "XHTML";
            case "HTML5":
                return "HTML 5";
            case "PLAIN":
                return "Plain Text";
            default:
                return "?";
        }
    }

    public int hashCode() {
        return Integer.hashCode(id);
    }

    public boolean equals(Object other) {
        if (other == null) {
            return false;
        }
        if (other == this) {
            return true;
        }
        if (other instanceof EdgeUrlDetails) {
            return ((EdgeUrlDetails) other).id == id;
        }
        return false;
    }
    public String getTitle() {
        if (title == null || title.isBlank()) {
            return url.toString();
        }
        return title;
    }

    public String getQualityPercent() {
        return String.format("%2.2f%%", 100*Math.exp(urlQuality+urlQualityAdjustment.getScore()));
    }
    public double getRanking() {
        double lengthAdjustment = Math.max(1, words / (words + 1000.));
        return (1+termScore)*Math.sqrt(1+rankingId)/Math.max(1E-10, lengthAdjustment *(0.7+0.3*Math.exp(urlQualityAdjustment.getScore())));
    }

    public int getSuperficialHash() {
        return Objects.hash(url.path, title);
    }
    public String getSuperficialHashStr() {
        return String.format("%8X", getSuperficialHash());
    }


    public String getGeminiLink() {
        return url.proto + "://" + url.domain.toString() + url.path.replace(" ", "%20").replace("\"", "%22");
    }
    public String getGeminiDescription() {
        return description.trim();
    }

    public boolean isPlainText() {
         return "PLAIN".equals(format);
    }

    public boolean isScripts() {
        return HtmlFeature.hasFeature(features, HtmlFeature.JS);
    }
    public boolean isTracking() {
        return HtmlFeature.hasFeature(features, HtmlFeature.TRACKING);
    }
    public boolean isAffiliate() {
        return HtmlFeature.hasFeature(features, HtmlFeature.AFFILIATE_LINK);
    }
    public boolean isMedia() {
        return HtmlFeature.hasFeature(features, HtmlFeature.MEDIA);
    }
    public boolean isCookies() {
        return HtmlFeature.hasFeature(features, HtmlFeature.COOKIES);
    }
    public boolean isSpecialDomain() {
        return domainState == EdgeDomainIndexingState.SPECIAL;
    }
    public int getLogRank() { return (int) Math.round(Math.min(Math.log(1+rankingId),10)); }

    public String getRankingSymbol() {
        return EdgeSearchRankingSymbols.getRankingSymbol(termScore);
    }

    public String getRankingSymbolDesc() {
        return EdgeSearchRankingSymbols.getRankingSymbolDescription(termScore);
    }
}
