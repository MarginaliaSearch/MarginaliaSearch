package nu.marginalia.search.model;

import nu.marginalia.api.searchquery.model.results.SearchResultItem;
import nu.marginalia.api.searchquery.model.results.SearchResultKeywordScore;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawl.DomainIndexingState;
import nu.marginalia.model.crawl.HtmlFeature;
import nu.marginalia.model.idx.DocumentMetadata;

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

    public int topology;
    public String positions;
    public long positionsMask;
    public int positionsCount;
    public SearchResultItem resultItem;
    public List<SearchResultKeywordScore> keywordScores;

    public UrlDetails(long id, int domainId, EdgeUrl url, String title, String description, String format, int features, DomainIndexingState domainState, double termScore, int resultsFromSameDomain, String positions, long positionsMask, int positionsCount, SearchResultItem resultItem, List<SearchResultKeywordScore> keywordScores) {
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
        this.positionsMask = positionsMask;
        this.topology = DocumentMetadata.decodeTopology(resultItem.encodedDocMetadata);
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
            case "PDF":
                return "PDF";
            default:
                return "?";
        }
    }

    public int hashCode() {
        return Long.hashCode(id);
    }

    /** Helper that inserts hyphenation hints and escapes
     * semantically meaningful codepoints into entity codes */
    public String displayTitle() {
        StringBuilder sb = new StringBuilder();

        int distSinceBreak = 0;

        char c = ' ';
        int prevC = ' ';
        for (int i = 0; i < title.length(); i++) {
            prevC = c;
            c = title.charAt(i);

            if (Character.isSpaceChar(c)) {
                distSinceBreak = 0;
            }
            else {
                distSinceBreak ++;
            }

            if (c == '<') {
                sb.append("&lt;");
            }
            else if (c == '>') {
                sb.append("&gt;");
            }
            else if (c == '&') {
                sb.append("&amp;");
            }
            else if (!Character.isAlphabetic(c) && !Character.isWhitespace(c)) {
                distSinceBreak = 0;
                sb.append(c);
                sb.append("&shy;");
            }
            else if (Character.isUpperCase(c) && Character.isLowerCase(prevC)) {
                distSinceBreak = 0;
                sb.append("&shy;");
                sb.append(c);
            }
            else if (distSinceBreak > 16) {
                distSinceBreak = 0;
                sb.append("&shy;");
                sb.append(c);
            }
            else {
                sb.append(c);
            }
        }

        return sb.toString();
    }

    /** Helper that inserts hyphenation hints and escapes
     * semantically meaningful codepoints into entity codes */
    public String displayDescription() {
        StringBuilder sb = new StringBuilder();

        int distSinceSpace = 0;
        for (int i = 0; i < description.length(); i++) {
            char c = description.charAt(i);
            if (Character.isSpaceChar(c)) {
                distSinceSpace = 0;
            }
            else {
                distSinceSpace ++;
            }

            if (c == '<') {
                sb.append("&lt;");
            }
            else if (c == '>') {
                sb.append("&gt;");
            }
            else if (c == '&') {
                sb.append("&amp;");
            }
            else if (!Character.isAlphabetic(c) && distSinceSpace > 24) {
                sb.append(c);
                sb.append("&shy;");
                distSinceSpace = 0;
            }
            else {
                sb.append(c);
            }
        }

        return sb.toString();
    }

    /** Helper that inserts hyphenation hints and escapes
     * semantically meaningful codepoints into entity codes */
    public String displayUrl() {
        StringBuilder sb = new StringBuilder();
        String urlStr = url.toDisplayString();
        for (int i = 0; i < urlStr.length(); i++) {
            char c = urlStr.charAt(i);

            if (c == '<') {
                sb.append("&lt;");
            }
            else if (c == '>') {
                sb.append("&gt;");
            }
            else if (c == '&') {
                sb.append("&amp;");
            }
            else if (!Character.isAlphabetic(c) && !Character.isWhitespace(c)) {
                sb.append(c);
                sb.append("&shy;");
            }
            else {
                sb.append(c);
            }
        }

        return sb.toString();
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

    public String toString() {
        return "UrlDetails(id=" + this.getId() + ", domainId=" + this.getDomainId() + ", url=" + this.getUrl() + ", title=" + this.getTitle() + ", description=" + this.getDescription() + ", format=" + this.getFormat() + ", features=" + this.getFeatures() + ", domainState=" + this.getDomainState() + ", termScore=" + this.getTermScore() + ", resultsFromSameDomain=" + this.getResultsFromSameDomain() + ", positions=" + this.getPositions() + ", positionsCount=" + this.getPositionsCount() + ", resultItem=" + this.getResultItem() + ", keywordScores=" + this.getKeywordScores() + ")";
    }

    public record UrlProblem(String name, String description) {

    }
}
