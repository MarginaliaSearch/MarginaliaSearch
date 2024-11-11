package nu.marginalia.integration.reddit.model;


/**
 * Corresponds directly to the pushshift.io Reddit comment JSON format.
 */
public class RawRedditComment {
    public String parent_id;
    public String link_id;
    public String id;
    public String author;
    public String body;
    public String subreddit;
    public int score;

    public RawRedditComment(String parent_id, String link_id, String id, String author, String body, String subreddit, int score) {
        this.parent_id = parent_id;
        this.link_id = link_id;
        this.id = id;
        this.author = author;
        this.body = body;
        this.subreddit = subreddit;
        this.score = score;
    }

    public RawRedditComment withParent_id(String parent_id) {
        return this.parent_id == parent_id ? this : new RawRedditComment(parent_id, this.link_id, this.id, this.author, this.body, this.subreddit, this.score);
    }

    public RawRedditComment withLink_id(String link_id) {
        return this.link_id == link_id ? this : new RawRedditComment(this.parent_id, link_id, this.id, this.author, this.body, this.subreddit, this.score);
    }

    public RawRedditComment withId(String id) {
        return this.id == id ? this : new RawRedditComment(this.parent_id, this.link_id, id, this.author, this.body, this.subreddit, this.score);
    }

    public RawRedditComment withAuthor(String author) {
        return this.author == author ? this : new RawRedditComment(this.parent_id, this.link_id, this.id, author, this.body, this.subreddit, this.score);
    }

    public RawRedditComment withBody(String body) {
        return this.body == body ? this : new RawRedditComment(this.parent_id, this.link_id, this.id, this.author, body, this.subreddit, this.score);
    }

    public RawRedditComment withSubreddit(String subreddit) {
        return this.subreddit == subreddit ? this : new RawRedditComment(this.parent_id, this.link_id, this.id, this.author, this.body, subreddit, this.score);
    }

    public RawRedditComment withScore(int score) {
        return this.score == score ? this : new RawRedditComment(this.parent_id, this.link_id, this.id, this.author, this.body, this.subreddit, score);
    }

    public String toString() {
        return "RawRedditComment(parent_id=" + this.parent_id + ", link_id=" + this.link_id + ", id=" + this.id + ", author=" + this.author + ", body=" + this.body + ", subreddit=" + this.subreddit + ", score=" + this.score + ")";
    }
}
