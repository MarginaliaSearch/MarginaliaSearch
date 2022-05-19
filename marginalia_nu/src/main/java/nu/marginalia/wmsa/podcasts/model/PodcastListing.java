package nu.marginalia.wmsa.podcasts.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

@AllArgsConstructor
@Getter
@Setter
@ToString
public class PodcastListing {
    public final List<PodcastMetadata> podcasts;
}
