package nu.marginalia.wmsa.api.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import nu.marginalia.wmsa.edge.model.search.EdgeUrlDetails;

@AllArgsConstructor @Getter
public class ApiSearchResult {
    public String url;
    public String title;
    public String description;
    public double quality;

    public ApiSearchResult(EdgeUrlDetails url) {
        this.url = url.url.toString();
        this.title = url.getTitle();
        this.description = url.getDescription();
        this.quality = url.getTermScore();
    }
}
