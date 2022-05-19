package nu.marginalia.wmsa.podcasts.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@AllArgsConstructor @Getter
public class PodcastNewEpisodes {
    public final List<PodcastEpisode> episodes;
}
