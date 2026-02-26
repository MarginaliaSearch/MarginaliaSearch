package nu.marginalia.api.model;

import java.util.ArrayList;
import java.util.List;

public class ApiSearchResult {
    public String url;
    public String title;
    public String description;
    public double quality;
    public String format; // "pdf", "html", "text", etc.

    public int resultsFromDomain;

    public List<List<ApiSearchResultQueryDetails>> details = new ArrayList<>();

    public ApiSearchResult(String url,
                           String title,
                           String description,
                           double quality,
                           String format,
                           int resultsFromDomain,
                           List<List<ApiSearchResultQueryDetails>> details) {
        this.url = url;
        this.title = title;
        this.description = description;
        this.quality = quality;
        this.format = format;
        this.resultsFromDomain = resultsFromDomain;
        this.details = details;
    }

    public String getUrl() {
        return this.url;
    }

    public String getTitle() {
        return this.title;
    }

    public String getDescription() {
        return this.description;
    }

    public double getQuality() {
        return this.quality;
    }

    public List<List<ApiSearchResultQueryDetails>> getDetails() {
        return this.details;
    }
}
