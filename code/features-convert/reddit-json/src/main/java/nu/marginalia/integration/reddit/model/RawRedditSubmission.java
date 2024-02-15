package nu.marginalia.integration.reddit.model;


import lombok.AllArgsConstructor;
import lombok.ToString;
import lombok.With;

/** Corresponds directly to the pushshift.io Reddit submission JSON format. */
@AllArgsConstructor
@With
@ToString
public class RawRedditSubmission {
    public int score;
    public String subreddit;
    public String name;
    public String author;
    public String title;
    public String selftext;
    public int num_comments;
    public int created_utc;
    public String permalink;
}
