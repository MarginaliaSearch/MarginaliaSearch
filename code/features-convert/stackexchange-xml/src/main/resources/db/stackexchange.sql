CREATE TABLE post (
    id INT PRIMARY KEY,
    threadId INT NOT NULL,
    postYear INT NOT NULL,
    title TEXT,
    body BINARY NOT NULL,
    origSize INTEGER NOT NULL,
    tags TEXT
);

CREATE INDEX post_threadId ON post(threadId);