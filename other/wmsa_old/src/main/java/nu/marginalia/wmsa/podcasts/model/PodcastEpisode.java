package nu.marginalia.wmsa.podcasts.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@AllArgsConstructor @Getter @ToString
public class PodcastEpisode {
    public final String podcastId;
    public final String podcastName;
    public final String guid;
    public final String title;
    public final String description;
    public final String dateUploaded;
    public final String mp3url;
}
