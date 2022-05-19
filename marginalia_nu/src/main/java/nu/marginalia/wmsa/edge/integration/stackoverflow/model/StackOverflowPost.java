package nu.marginalia.wmsa.edge.integration.stackoverflow.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;
import nu.marginalia.wmsa.edge.model.EdgeUrl;

@Data @AllArgsConstructor @ToString
public class StackOverflowPost {
    public EdgeUrl url;
    public String title;
    public String fullBody;
    public String justBody;
}
