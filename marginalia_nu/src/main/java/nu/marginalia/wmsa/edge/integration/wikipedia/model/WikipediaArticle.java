package nu.marginalia.wmsa.edge.integration.wikipedia.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import nu.marginalia.wmsa.edge.model.EdgeUrl;

@Data
@AllArgsConstructor
public class WikipediaArticle {
    public final EdgeUrl url;
    public final String body;
}
