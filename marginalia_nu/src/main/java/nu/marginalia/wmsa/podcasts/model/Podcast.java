package nu.marginalia.wmsa.podcasts.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor @Getter @Setter @ToString
public class Podcast {
    public final PodcastMetadata metadata;

    public final List<PodcastEpisode> episodes = new ArrayList<>();
}
