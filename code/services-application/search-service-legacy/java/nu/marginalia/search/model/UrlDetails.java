package nu.marginalia.search.model;

import nu.marginalia.api.searchquery.model.results.SearchResultItem;
import nu.marginalia.api.searchquery.model.results.SearchResultKeywordScore;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawl.DomainIndexingState;
import nu.marginalia.model.crawl.HtmlFeature;

import java.util.ArrayList;
import java.util.List;

/**
 * A class to hold details about a single search result.
 */
public class UrlDetails implements Comparable<UrlDetails> {
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

    public UrlDetails(long id, int domainId, EdgeUrl url, String title, String description, String format, int features, DomainIndexingState domainState, double termScore, int resultsFromSameDomain, String positions, int positionsCount, SearchResultItem resultItem, List<SearchResultKeywordScore> keywordScores) {
        this.id = id;
        this.domainId = domainId;
        this.url = url;
        this.title = title;
        this.description = description;
        this.format = format;
        this.features = features;
        this.domainState = domainState;
        this.termScore = termScore;
        this.resultsFromSameDomain = resultsFromSameDomain;
        this.positions = positions;
        this.positionsCount = positionsCount;
        this.resultItem = resultItem;
        this.keywordScores = keywordScores;
    }

    public UrlDetails() {
    }

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

    @Override
    public int compareTo(UrlDetails other) {
        int result = Double.compare(getTermScore(), other.getTermScore());
        if (result == 0) result = Long.compare(getId(), other.getId());
        return result;
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

    public boolean isAds() {
        return HtmlFeature.hasFeature(features, HtmlFeature.TRACKING_ADTECH);
    }

    public int getMatchRank() {
        if (termScore <= 1) return 1;
        if (termScore <= 2) return 2;
        if (termScore <= 3) return 3;
        if (termScore <= 5) return 5;

        return 10;
    }

    public long getId() {
        return this.id;
    }

    public int getDomainId() {
        return this.domainId;
    }

    public EdgeUrl getUrl() {
        return this.url;
    }

    public String getDescription() {
        return this.description;
    }

    public int getFeatures() {
        return this.features;
    }

    public DomainIndexingState getDomainState() {
        return this.domainState;
    }

    public double getTermScore() {
        return this.termScore;
    }

    public int getResultsFromSameDomain() {
        return this.resultsFromSameDomain;
    }

    public String getPositions() {
        return this.positions;
    }

    public int getPositionsCount() {
        return this.positionsCount;
    }

    public SearchResultItem getResultItem() {
        return this.resultItem;
    }

    public List<SearchResultKeywordScore> getKeywordScores() {
        return this.keywordScores;
    }

    public UrlDetails withId(long id) {
        return this.id == id ? this : new UrlDetails(id, this.domainId, this.url, this.title, this.description, this.format, this.features, this.domainState, this.termScore, this.resultsFromSameDomain, this.positions, this.positionsCount, this.resultItem, this.keywordScores);
    }

    public UrlDetails withDomainId(int domainId) {
        return this.domainId == domainId ? this : new UrlDetails(this.id, domainId, this.url, this.title, this.description, this.format, this.features, this.domainState, this.termScore, this.resultsFromSameDomain, this.positions, this.positionsCount, this.resultItem, this.keywordScores);
    }

    public UrlDetails withUrl(EdgeUrl url) {
        return this.url == url ? this : new UrlDetails(this.id, this.domainId, url, this.title, this.description, this.format, this.features, this.domainState, this.termScore, this.resultsFromSameDomain, this.positions, this.positionsCount, this.resultItem, this.keywordScores);
    }

    public UrlDetails withTitle(String title) {
        return this.title == title ? this : new UrlDetails(this.id, this.domainId, this.url, title, this.description, this.format, this.features, this.domainState, this.termScore, this.resultsFromSameDomain, this.positions, this.positionsCount, this.resultItem, this.keywordScores);
    }

    public UrlDetails withDescription(String description) {
        return this.description == description ? this : new UrlDetails(this.id, this.domainId, this.url, this.title, description, this.format, this.features, this.domainState, this.termScore, this.resultsFromSameDomain, this.positions, this.positionsCount, this.resultItem, this.keywordScores);
    }

    public UrlDetails withFormat(String format) {
        return this.format == format ? this : new UrlDetails(this.id, this.domainId, this.url, this.title, this.description, format, this.features, this.domainState, this.termScore, this.resultsFromSameDomain, this.positions, this.positionsCount, this.resultItem, this.keywordScores);
    }

    public UrlDetails withFeatures(int features) {
        return this.features == features ? this : new UrlDetails(this.id, this.domainId, this.url, this.title, this.description, this.format, features, this.domainState, this.termScore, this.resultsFromSameDomain, this.positions, this.positionsCount, this.resultItem, this.keywordScores);
    }

    public UrlDetails withDomainState(DomainIndexingState domainState) {
        return this.domainState == domainState ? this : new UrlDetails(this.id, this.domainId, this.url, this.title, this.description, this.format, this.features, domainState, this.termScore, this.resultsFromSameDomain, this.positions, this.positionsCount, this.resultItem, this.keywordScores);
    }

    public UrlDetails withTermScore(double termScore) {
        return this.termScore == termScore ? this : new UrlDetails(this.id, this.domainId, this.url, this.title, this.description, this.format, this.features, this.domainState, termScore, this.resultsFromSameDomain, this.positions, this.positionsCount, this.resultItem, this.keywordScores);
    }

    public UrlDetails withResultsFromSameDomain(int resultsFromSameDomain) {
        return this.resultsFromSameDomain == resultsFromSameDomain ? this : new UrlDetails(this.id, this.domainId, this.url, this.title, this.description, this.format, this.features, this.domainState, this.termScore, resultsFromSameDomain, this.positions, this.positionsCount, this.resultItem, this.keywordScores);
    }

    public UrlDetails withPositions(String positions) {
        return this.positions == positions ? this : new UrlDetails(this.id, this.domainId, this.url, this.title, this.description, this.format, this.features, this.domainState, this.termScore, this.resultsFromSameDomain, positions, this.positionsCount, this.resultItem, this.keywordScores);
    }

    public UrlDetails withPositionsCount(int positionsCount) {
        return this.positionsCount == positionsCount ? this : new UrlDetails(this.id, this.domainId, this.url, this.title, this.description, this.format, this.features, this.domainState, this.termScore, this.resultsFromSameDomain, this.positions, positionsCount, this.resultItem, this.keywordScores);
    }

    public UrlDetails withResultItem(SearchResultItem resultItem) {
        return this.resultItem == resultItem ? this : new UrlDetails(this.id, this.domainId, this.url, this.title, this.description, this.format, this.features, this.domainState, this.termScore, this.resultsFromSameDomain, this.positions, this.positionsCount, resultItem, this.keywordScores);
    }

    public UrlDetails withKeywordScores(List<SearchResultKeywordScore> keywordScores) {
        return this.keywordScores == keywordScores ? this : new UrlDetails(this.id, this.domainId, this.url, this.title, this.description, this.format, this.features, this.domainState, this.termScore, this.resultsFromSameDomain, this.positions, this.positionsCount, this.resultItem, keywordScores);
    }

    public String toString() {
        return "UrlDetails(id=" + this.getId() + ", domainId=" + this.getDomainId() + ", url=" + this.getUrl() + ", title=" + this.getTitle() + ", description=" + this.getDescription() + ", format=" + this.getFormat() + ", features=" + this.getFeatures() + ", domainState=" + this.getDomainState() + ", termScore=" + this.getTermScore() + ", resultsFromSameDomain=" + this.getResultsFromSameDomain() + ", positions=" + this.getPositions() + ", positionsCount=" + this.getPositionsCount() + ", resultItem=" + this.getResultItem() + ", keywordScores=" + this.getKeywordScores() + ")";
    }

    public static record UrlProblem(String name, String description) {

    }
}
