CREATE TABLE submission (
    id TEXT PRIMARY KEY,
    author TEXT,
    subreddit TEXT,
    title TEXT,
    selftext TEXT,
    score INTEGER,
    created_utc INTEGER,
    num_comments INTEGER,
    permalink TEXT
);

CREATE TABLE comment (
    id TEXT PRIMARY KEY,
    threadId TEXT,
    author TEXT,
    subreddit TEXT,
    title TEXT,
    body TEXT,
    score INTEGER,
    num_comments INTEGER
);

CREATE INDEX submission_id ON submission(id);
CREATE INDEX comment_id ON comment(id);