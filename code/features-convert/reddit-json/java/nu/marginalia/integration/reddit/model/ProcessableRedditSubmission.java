package nu.marginalia.integration.reddit.model;

import lombok.AllArgsConstructor;
import lombok.ToString;

/** A projection of a Reddit top level submission that is appropriate for processing. */
@AllArgsConstructor @ToString
public class ProcessableRedditSubmission {
    public String subreddit;
    public String name;
    public String author;
    public String title;
    public String selftext;
    public int created_utc;
    public String permalink;
    public int score;
}
