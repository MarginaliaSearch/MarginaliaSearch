package nu.marginalia.integration.reddit.model;

/**
 * A projection of a Reddit comment joined with its top level submission
 * that is ready for processing.
 */
public class ProcessableRedditComment {
    public String subreddit;
    public String name;
    public String author;
    public String title;
    public String body;
    public int created_utc;
    public String permalink;
    public int score;

    public ProcessableRedditComment(String subreddit, String name, String author, String title, String body, int created_utc, String permalink, int score) {
        this.subreddit = subreddit;
        this.name = name;
        this.author = author;
        this.title = title;
        this.body = body;
        this.created_utc = created_utc;
        this.permalink = permalink;
        this.score = score;
    }

    public String toString() {
        return "ProcessableRedditComment(subreddit=" + this.subreddit + ", name=" + this.name + ", author=" + this.author + ", title=" + this.title + ", body=" + this.body + ", created_utc=" + this.created_utc + ", permalink=" + this.permalink + ", score=" + this.score + ")";
    }
}
