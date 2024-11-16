package nu.marginalia.integration.reddit.model;


/**
 * Corresponds directly to the pushshift.io Reddit submission JSON format.
 */
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

    public RawRedditSubmission(int score, String subreddit, String name, String author, String title, String selftext, int num_comments, int created_utc, String permalink) {
        this.score = score;
        this.subreddit = subreddit;
        this.name = name;
        this.author = author;
        this.title = title;
        this.selftext = selftext;
        this.num_comments = num_comments;
        this.created_utc = created_utc;
        this.permalink = permalink;
    }

    public RawRedditSubmission withScore(int score) {
        return this.score == score ? this : new RawRedditSubmission(score, this.subreddit, this.name, this.author, this.title, this.selftext, this.num_comments, this.created_utc, this.permalink);
    }

    public RawRedditSubmission withSubreddit(String subreddit) {
        return this.subreddit == subreddit ? this : new RawRedditSubmission(this.score, subreddit, this.name, this.author, this.title, this.selftext, this.num_comments, this.created_utc, this.permalink);
    }

    public RawRedditSubmission withName(String name) {
        return this.name == name ? this : new RawRedditSubmission(this.score, this.subreddit, name, this.author, this.title, this.selftext, this.num_comments, this.created_utc, this.permalink);
    }

    public RawRedditSubmission withAuthor(String author) {
        return this.author == author ? this : new RawRedditSubmission(this.score, this.subreddit, this.name, author, this.title, this.selftext, this.num_comments, this.created_utc, this.permalink);
    }

    public RawRedditSubmission withTitle(String title) {
        return this.title == title ? this : new RawRedditSubmission(this.score, this.subreddit, this.name, this.author, title, this.selftext, this.num_comments, this.created_utc, this.permalink);
    }

    public RawRedditSubmission withSelftext(String selftext) {
        return this.selftext == selftext ? this : new RawRedditSubmission(this.score, this.subreddit, this.name, this.author, this.title, selftext, this.num_comments, this.created_utc, this.permalink);
    }

    public RawRedditSubmission withNum_comments(int num_comments) {
        return this.num_comments == num_comments ? this : new RawRedditSubmission(this.score, this.subreddit, this.name, this.author, this.title, this.selftext, num_comments, this.created_utc, this.permalink);
    }

    public RawRedditSubmission withCreated_utc(int created_utc) {
        return this.created_utc == created_utc ? this : new RawRedditSubmission(this.score, this.subreddit, this.name, this.author, this.title, this.selftext, this.num_comments, created_utc, this.permalink);
    }

    public RawRedditSubmission withPermalink(String permalink) {
        return this.permalink == permalink ? this : new RawRedditSubmission(this.score, this.subreddit, this.name, this.author, this.title, this.selftext, this.num_comments, this.created_utc, permalink);
    }

    public String toString() {
        return "RawRedditSubmission(score=" + this.score + ", subreddit=" + this.subreddit + ", name=" + this.name + ", author=" + this.author + ", title=" + this.title + ", selftext=" + this.selftext + ", num_comments=" + this.num_comments + ", created_utc=" + this.created_utc + ", permalink=" + this.permalink + ")";
    }
}
