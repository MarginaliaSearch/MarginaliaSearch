package nu.marginalia.wmsa.edge.model.crawl;


import lombok.*;

@AllArgsConstructor
@EqualsAndHashCode
@Getter
@Setter
@Builder
@ToString
public class EdgeContentType {
    public final String contentType;
    public final String charset;
}
