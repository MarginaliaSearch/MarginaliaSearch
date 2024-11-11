package nu.marginalia.integration.reddit.model;

/**
 * A projection of a Reddit top level submission that is appropriate for processing.
 */
public class ProcessableRedditSubmission {
    public String subreddit;
    public String name;
    public String author;
    public String title;
    public String selftext;
    public int created_utc;
    public String permalink;
    public int score;

    public ProcessableRedditSubmission(String subreddit, String name, String author, String title, String selftext, int created_utc, String permalink, int score) {
        this.subreddit = subreddit;
        this.name = name;
        this.author = author;
        this.title = title;
        this.selftext = selftext;
        this.created_utc = created_utc;
        this.permalink = permalink;
        this.score = score;
    }

    public String toString() {
        return "ProcessableRedditSubmission(subreddit=" + this.subreddit + ", name=" + this.name + ", author=" + this.author + ", title=" + this.title + ", selftext=" + this.selftext + ", created_utc=" + this.created_utc + ", permalink=" + this.permalink + ", score=" + this.score + ")";
    }
}
