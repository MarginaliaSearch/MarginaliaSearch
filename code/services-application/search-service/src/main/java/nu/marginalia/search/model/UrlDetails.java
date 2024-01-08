package nu.marginalia.search.model;

import lombok.*;
import nu.marginalia.index.client.model.results.SearchResultItem;
import nu.marginalia.index.client.model.results.SearchResultKeywordScore;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawl.DomainIndexingState;
import nu.marginalia.model.crawl.HtmlFeature;

import java.util.EnumSet;
import java.util.List;
import java.util.StringJoiner;

@AllArgsConstructor @NoArgsConstructor @With @Getter @ToString
public class UrlDetails {
    public long id;
    public int domainId;
    public EdgeUrl url;
    public String title;
    public String description;

    public double urlQuality;

    public int words;
    public String format;
    public int features;

    public String ip;
    public DomainIndexingState domainState;

    public long dataHash;

    public PageScoreAdjustment urlQualityAdjustment;
    public long rankingId;
    public double termScore;

    public int resultsFromSameDomain;

    public String positions;
    public SearchResultItem resultItem;
    public List<SearchResultKeywordScore> keywordScores;
    public long combinedId;

    public boolean hasMoreResults() {
        return resultsFromSameDomain > 1;
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
        return Long.hashCode(id);
    }

    public boolean equals(Object other) {
        if (other == null) {
            return false;
        }
        if (other == this) {
            return true;
        }
        if (other instanceof UrlDetails) {
            return ((UrlDetails) other).id == id;
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
        double lengthAdjustment = Math.max(1, words / (words + 10000.));
        return getFeatureScore()*Math.sqrt(1+rankingId)/Math.max(1E-10, lengthAdjustment *(0.7+0.3*Math.exp(urlQualityAdjustment.getScore())));
    }

    public boolean isPlainText() {
         return "PLAIN".equals(format);
    }

    public int getProblemCount() {
        int numProblems = 0;

        int mask = HtmlFeature.JS.getFeatureBit()
                | HtmlFeature.COOKIES.getFeatureBit()
                | HtmlFeature.TRACKING.getFeatureBit()
                | HtmlFeature.AFFILIATE_LINK.getFeatureBit()
                | HtmlFeature.TRACKING_ADTECH.getFeatureBit()
                | HtmlFeature.ADVERTISEMENT.getFeatureBit();

        return Integer.bitCount(features & mask);
    }

    public String getProblems() {
        StringJoiner sj = new StringJoiner(", ");

        if (isScripts()) {
            sj.add("Javascript");
        }
        if (isCookies()) {
            sj.add("Cookies");
        }
        if (isTracking()) {
            sj.add("Tracking/Analytics");
        }
        if (isAffiliate()) {
            sj.add("Affiliate Linking");
        }
        if (isAds()) {
            sj.add("Ads/Adtech Tracking");
        }
        return sj.toString();

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
    public boolean isAds() { return HtmlFeature.hasFeature(features, HtmlFeature.TRACKING_ADTECH); }

    public int getLogRank() { return (int) Math.round(Math.min(Math.log(1+rankingId),10)); }

    public int getMatchRank() {
        if (termScore <= 1) return 1;
        if (termScore <= 2) return 2;
        if (termScore <= 3) return 3;
        if (termScore <= 5) return 5;

        return 10;
    }

    public double getFeatureScore() {
        double score = 1;
        if (isScripts()) {
            score+=1;
        } else if(!"HTML5".equals(format)) {
            score+=0.5;
        }
        if (isAffiliate()) {
            score += 2.5;
        }
        if (isTracking()) {
            score += 1.5;
        }
        if (isCookies()) {
            score += 1.5;
        }
        return score;
    }
}
