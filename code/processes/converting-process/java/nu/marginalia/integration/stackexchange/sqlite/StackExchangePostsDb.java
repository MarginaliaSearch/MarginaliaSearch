package nu.marginalia.integration.stackexchange.sqlite;

import com.github.luben.zstd.Zstd;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import nu.marginalia.integration.stackexchange.xml.StackExchangeXmlPostReader;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.function.Predicate;

/**  Because stackexchange's XML format is a stream of entities that reference their parent,
 * and we want to process them in a thread-by-thread order, it is necessary to use something
 * to essentially re-order the data.
 * <p>
 * This class uses SQLite to perform this task.  The actual post bodies are compressed to keep
 * the size of the file down.  It is strongly advisable to read off an SSD and not a mechanical
 * hard drive when processing these database files, the difference in processing time is 20 minutes
 * vs 6+ hours.
 * <p>
 */
public class StackExchangePostsDb {

    /** Construct a SQLIte file containing the Posts in the stack exchange-style 7z file */
    public static void create(String domain,
                              Path sqliteFile,
                              Path stackExchange7zFile) throws IOException {
        Files.deleteIfExists(sqliteFile);

        String connStr = "jdbc:sqlite:" + sqliteFile;

        try (var connection = DriverManager.getConnection(connStr);
             var stream = ClassLoader.getSystemResourceAsStream("db/stackexchange.sql");
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

            var postReader = new StackExchangeXmlPostReader(
                    stackExchange7zFile
            );

            var insertMeta = connection.prepareStatement("""
                    INSERT INTO metadata(domainName)
                    VALUES (?)
                    """);
            insertMeta.setString(1, domain);
            insertMeta.executeUpdate();

            var insertPost = connection.prepareStatement("""
                     INSERT INTO post(id, threadId, postYear, title, body, origSize, tags)
                     VALUES (?, ?, ?, ?, ?, ?, ?)
                     """);

            var iter = postReader.iterator();

            int cnt = 0;
            while (iter.hasNext()) {
                var post = iter.next();
                insertPost.setInt(1, post.id());

                // We invent a new field called threadId, which is the id of the post if it's
                // a question, or the parent if it's an answer
                if (post.parentId() == null) insertPost.setInt(2, post.id());
                else insertPost.setInt(2, post.parentId());

                insertPost.setInt(3, post.year());

                if (post.title() == null)
                    insertPost.setString(4, "");
                else
                    insertPost.setString(4, post.title());

                byte[] bodyBytes = post.body().getBytes();
                insertPost.setBytes(5, Zstd.compress(bodyBytes));
                insertPost.setInt(6, bodyBytes.length);

                insertPost.setString(7, String.join(",", post.tags()));
                insertPost.addBatch();
                if (++cnt > 100) {
                    insertPost.executeBatch();
                    cnt = 0;
                }
            }
            if (cnt > 0) {
                insertPost.executeBatch();
            }
        }
        catch (IOException | SQLException | XMLStreamException e) {
            e.printStackTrace();
        }
    }

    /** Iterate over each post in the sqlite post database.
     * Each post will be assigned an ordinal number that is different from the id of the post.  This is
     * necessary as stackexchange's entry count exceeds the ~67 million entries that UrlIdCodec can encode
     * for a single domain, despite having less than 67 million 'threads'.
     * */
    public static void forEachPost(
            Path sqliteFile,
            Predicate<CombinedPostModel> consumer) {

        String connStr = "jdbc:sqlite:" + sqliteFile;


        try (var connection = DriverManager.getConnection(connStr);
             var selectThreadIds = connection.prepareStatement("SELECT DISTINCT(threadId) FROM post");
             var queryPostContents = connection.prepareStatement("""
                    SELECT postYear, title, body, origSize, tags
                    FROM post
                    WHERE threadId = ?
                    """)
        ) {

            // Step 1 is to export a list of thread IDs from the database
            TIntList threadIds = new TIntArrayList(10_000);
            ResultSet rs = selectThreadIds.executeQuery();
            while (rs.next()) {
                threadIds.add(rs.getInt(1));
            }

            System.out.println("Got " + threadIds.size() + " IDs");

            // Step 2: Iterate over each thread
            var idIterator = threadIds.iterator();
            int ordinal = 0;
            while (idIterator.hasNext()) {
                int threadId = idIterator.next();

                // Query posts with this threadId
                queryPostContents.setInt(1, threadId);
                rs = queryPostContents.executeQuery();

                List<String> parts = new ArrayList<>();
                String title = "";
                int year = 2023;

                String tags = "";

                List<Future<String>> partWork = new ArrayList<>();
                var commonPool = ForkJoinPool.commonPool();
                while (rs.next()) {
                    String maybeTitle = rs.getString("title");

                    if (maybeTitle != null && !maybeTitle.isBlank())
                        title = maybeTitle;

                    String maybeTags = rs.getString("tags");
                    if (maybeTags != null && !maybeTags.isBlank())
                        tags = maybeTags;

                    int origSize = rs.getInt("origSize");

                    year = Math.min(year, rs.getInt("postYear"));

                    // Decompress the bodies
                    byte[] bytes = rs.getBytes("body");
                    partWork.add(commonPool.submit(
                            () -> new String(Zstd.decompress(bytes, origSize)
                    )));
                }

                for (var workItem : partWork) {
                    parts.add(workItem.get());
                }

                if (!consumer.test(new CombinedPostModel(ordinal++, threadId, title, year, parts, tags)))
                    break;
            }

        }
        catch (SQLException | InterruptedException | ExecutionException ex) {
            throw new RuntimeException(ex);
        }

    }

    public static String getDomainName(Path pathToDbFile) throws SQLException {
        String connStr = "jdbc:sqlite:" + pathToDbFile;

        try (var connection = DriverManager.getConnection(connStr);
             var stmt = connection.prepareStatement("SELECT domainName FROM metadata")
        ) {
            var rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString(1);
            }
            throw new IllegalArgumentException("No metadata in db file " + pathToDbFile);
        }

    }

    public record CombinedPostModel(int ordinal,
                                    int threadId,
                                    String title,
                                    int year,
                                    List<String> bodies,
                                    String tags)
    { }

}
