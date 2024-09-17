package nu.marginalia.integration.reddit.model;

import lombok.AllArgsConstructor;
import lombok.ToString;

/** A projection of a Reddit comment joined with its top level submission
 * that is ready for processing. */
@AllArgsConstructor
@ToString
public class ProcessableRedditComment {
    public String subreddit;
    public String name;
    public String author;
    public String title;
    public String body;
    public int created_utc;
    public String permalink;
    public int score;
}
