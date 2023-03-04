package nu.marginalia.wmsa.podcasts.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@AllArgsConstructor
@Getter
@Setter
@ToString
public class PodcastMetadata {
    public final String title;
    public final String description;
    public final String id;
    public final String extLink;
}
