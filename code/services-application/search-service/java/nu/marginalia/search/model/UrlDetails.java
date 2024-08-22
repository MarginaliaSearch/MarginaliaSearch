package nu.marginalia.search.model;

import lombok.*;
import nu.marginalia.api.searchquery.model.results.SearchResultItem;
import nu.marginalia.api.searchquery.model.results.SearchResultKeywordScore;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawl.DomainIndexingState;
import nu.marginalia.model.crawl.HtmlFeature;

import java.util.ArrayList;
import java.util.List;

/** A class to hold details about a single search result. */
@AllArgsConstructor @NoArgsConstructor @With @Getter @ToString
public class UrlDetails {
    public long id;
    public int domainId;

    public EdgeUrl url;
    public String title;
    public String description;

    public String format;
    public int features;

    public DomainIndexingState domainState;

    public double termScore;

    public int resultsFromSameDomain;

    public String positions;
    public int positionsCount;
    public SearchResultItem resultItem;
    public List<SearchResultKeywordScore> keywordScores;

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

    public boolean isPlainText() {
         return "PLAIN".equals(format);
    }

    public int getProblemCount() {
        int mask = HtmlFeature.JS.getFeatureBit()
                | HtmlFeature.COOKIES.getFeatureBit()
                | HtmlFeature.TRACKING.getFeatureBit()
                | HtmlFeature.AFFILIATE_LINK.getFeatureBit()
                | HtmlFeature.TRACKING_ADTECH.getFeatureBit()
                | HtmlFeature.ADVERTISEMENT.getFeatureBit();

        return Integer.bitCount(features & mask);
    }

    public List<UrlProblem> getProblems() {
        List<UrlProblem> problems = new ArrayList<>();

        if (isScripts()) {
            problems.add(new UrlProblem("Js", "The page uses Javascript"));
        }
        if (isCookies()) {
            problems.add(new UrlProblem("Co", "The page uses Cookies"));
        }
        if (isTracking()) {
            problems.add(new UrlProblem("Tr", "The page uses Tracking/Analytics"));
        }
        if (isAffiliate()) {
            problems.add(new UrlProblem("Af", "The page may use Affiliate Linking"));
        }
        if (isAds()) {
            problems.add(new UrlProblem("Ad", "The page uses Ads/Adtech Tracking"));
        }
        return problems;

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

    public int getMatchRank() {
        if (termScore <= 1) return 1;
        if (termScore <= 2) return 2;
        if (termScore <= 3) return 3;
        if (termScore <= 5) return 5;

        return 10;
    }

    public static record UrlProblem(String name, String description) {

    }
}
