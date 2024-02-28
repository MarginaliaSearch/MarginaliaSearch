package nu.marginalia.integration.reddit.model;


import lombok.AllArgsConstructor;
import lombok.ToString;
import lombok.With;

/** Corresponds directly to the pushshift.io Reddit comment JSON format. */
@AllArgsConstructor
@ToString
@With
public class RawRedditComment {
    public String parent_id;
    public String link_id;
    public String id;
    public String author;
    public String body;
    public String subreddit;
    public int score;
}
