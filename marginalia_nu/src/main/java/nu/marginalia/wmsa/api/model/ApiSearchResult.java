package nu.marginalia.wmsa.api.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import nu.marginalia.wmsa.edge.model.search.EdgeSearchResultKeywordScore;
import nu.marginalia.wmsa.edge.model.search.EdgeUrlDetails;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@AllArgsConstructor @Getter
public class ApiSearchResult {
    public String url;
    public String title;
    public String description;
    public double quality;

    public List<List<ApiSearchResultQueryDetails>> details = new ArrayList<>();

    public ApiSearchResult(EdgeUrlDetails url) {
        this.url = url.url.toString();
        this.title = url.getTitle();
        this.description = url.getDescription();

        this.quality = sanitizeNaN(url.getTermScore(), -100);

        if (url.resultItem != null) {
            var bySet = url.resultItem.scores.stream().collect(Collectors.groupingBy(EdgeSearchResultKeywordScore::set));

            outer:
            for (var entries : bySet.values()) {
                List<ApiSearchResultQueryDetails> lst = new ArrayList<>();
                for (var entry : entries) {
                    var metadata = entry.metadata();
                    if (metadata.isEmpty())
                        continue outer;

                    Set<String> flags = metadata.flags().stream().map(Object::toString).collect(Collectors.toSet());
                    lst.add(new ApiSearchResultQueryDetails(entry.keyword(), metadata.tfIdf(),metadata.count(), flags));
                }
                details.add(lst);
            }
        }
    }

    private double sanitizeNaN(double value, double alternative) {
        if (!Double.isFinite(value)) {
            return alternative;
        }
        return value;
    }
}
