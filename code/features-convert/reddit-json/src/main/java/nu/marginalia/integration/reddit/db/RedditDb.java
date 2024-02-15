package nu.marginalia.integration.reddit.db;

import com.google.common.base.Strings;
import lombok.SneakyThrows;
import nu.marginalia.integration.reddit.RedditEntryReader;
import nu.marginalia.integration.reddit.model.ProcessableRedditComment;
import nu.marginalia.integration.reddit.model.ProcessableRedditSubmission;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.Iterator;

/** This class is responsible for creating and accessing a sqlite database with reddit data, for
 *  easier aggregation and subsequent processing. */
public class RedditDb {
    public static void create(Path submissionsFile, Path commentsFile, Path dbFile) throws IOException, SQLException
    {
        Files.deleteIfExists(dbFile);

        try (var connection = DriverManager.getConnection(STR."jdbc:sqlite:\{dbFile}");
             var stream = ClassLoader.getSystemResourceAsStream("db/reddit.sql");
             var updateStmt = connection.createStatement()
        ) {
            var sql = new String(stream.readAllBytes());

            String[] sqlParts = sql.split(";");
            for (var part : sqlParts) {
                if (part.isBlank()) {
                    continue;
                }
                updateStmt.executeUpdate(part);
            }
            updateStmt.execute("PRAGMA synchronous = OFF");


            try (var iter = RedditEntryReader.readSubmissions(submissionsFile);
                 var stmt = connection.prepareStatement("""
                     INSERT OR IGNORE INTO submission(id, author, created_utc, score, title, selftext, subreddit, permalink)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                     """)
            ) {
                while (iter.hasNext()) {
                    var submission = iter.next();

                    if (Strings.isNullOrEmpty(submission.name))
                        continue;

                    stmt.setString(1, submission.name);
                    stmt.setString(2, submission.author);
                    stmt.setInt(3, submission.created_utc);
                    stmt.setInt(4, submission.score);
                    stmt.setString(5, submission.title);
                    if (submission.score > 10 && submission.selftext.length() > 1000) {
                        stmt.setString(6, submission.selftext);
                    } else {
                        stmt.setString(6, "");
                    }
                    stmt.setString(7, submission.subreddit);
                    stmt.setString(8, submission.permalink);
                    stmt.executeUpdate();
                }

            }
            try (var iter = RedditEntryReader.readComments(commentsFile);
                    var stmt = connection.prepareStatement("""
                        INSERT OR IGNORE INTO comment(id, author, score, body, threadId)
                        VALUES (?, ?, ?, ?, ?)
                        """)
                    )
            {
                while (iter.hasNext()) {
                    var comment = iter.next();

                    if (comment.body.length() < 1000) continue;
                    if (comment.score < 10) continue;

                    // We only want to store top-level comments
                    if (!comment.parent_id.startsWith("t3")) continue;

                    stmt.setString(1, comment.id);
                    stmt.setString(2, comment.author);
                    stmt.setInt(3, comment.score);
                    stmt.setString(4, comment.body);
                    stmt.setString(5, comment.parent_id);
                    stmt.executeUpdate();
                }
            }
        }
    }

    public static SubmissionIterator getSubmissions(Path file) throws SQLException {
        var connection = DriverManager.getConnection(STR."jdbc:sqlite:\{file}");

        return new SubmissionIterator(connection);
    }
    public static CommentIterator getComments(Path file) throws SQLException {
        var connection = DriverManager.getConnection(STR."jdbc:sqlite:\{file}");

        return new CommentIterator(connection);
    }

    public static class SubmissionIterator extends SqlQueryIterator<ProcessableRedditSubmission> {

        SubmissionIterator(Connection connection) throws SQLException {
            super(connection, """
                SELECT id, author, created_utc, score, title, selftext, subreddit, permalink
                FROM submission
                WHERE length(selftext) > 0
                """);
        }

        @Override
        ProcessableRedditSubmission nextFromResultSet(ResultSet resultSet) throws SQLException {
            return new ProcessableRedditSubmission(resultSet.getString("subreddit"),
                    resultSet.getString("id"),
                    resultSet.getString("author"),
                    resultSet.getString("title"),
                    resultSet.getString("selftext"),
                    resultSet.getInt("created_utc"),
                    resultSet.getString("permalink"),
                    resultSet.getInt("score")
            );
        }
    }

    public static class CommentIterator extends SqlQueryIterator<ProcessableRedditComment> {

        CommentIterator(Connection connection) throws SQLException {
            super(connection, """
                select submission.subreddit,
                       comment.id,
                       comment.author,
                       submission.title,
                       body,
                       created_utc,
                       permalink,
                       comment.score
                from comment
                inner join submission on threadId=submission.id
                """);
        }

        @Override
        ProcessableRedditComment nextFromResultSet(ResultSet resultSet) throws SQLException {
            return new ProcessableRedditComment(
                    resultSet.getString("subreddit"),
                    resultSet.getString("id"),
                    resultSet.getString("author"),
                    resultSet.getString("title"),
                    resultSet.getString("body"),
                    resultSet.getInt("created_utc"),
                    resultSet.getString("permalink") + resultSet.getString("id"),
                    resultSet.getInt("score")
            );
        }
    }


    static abstract class SqlQueryIterator<T> implements Iterator<T>, AutoCloseable {
        private final PreparedStatement stmt;
        private final ResultSet resultSet;

        private Boolean hasNext = null;
        SqlQueryIterator(Connection connection, String query) throws SQLException {
            // This is sql-injection safe since the query is not user input:
            stmt = connection.prepareStatement(query);

            resultSet = stmt.executeQuery();
        }
        @Override
        public void close() throws Exception {
            resultSet.close();
            stmt.close();
        }

        @SneakyThrows
        @Override
        public boolean hasNext() {
            if (hasNext != null)
                return hasNext;

            hasNext = resultSet.next();

            return hasNext;
        }

        abstract T nextFromResultSet(ResultSet resultSet) throws SQLException;

        @SneakyThrows
        @Override
        public T next() {
            if (!hasNext())
                throw new IllegalStateException();
            else hasNext = null;

            return nextFromResultSet(resultSet);

        }
    }

}
