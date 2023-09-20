package nu.marginalia.integration.stackexchange.sqlite;

import com.github.luben.zstd.Zstd;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TIntIntMap;
import gnu.trove.map.hash.TIntIntHashMap;
import lombok.SneakyThrows;
import nu.marginalia.integration.stackexchange.xml.StackExchangeXmlPostReader;
import org.apache.commons.compress.compressors.zstandard.ZstdUtils;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.function.Predicate;

public class StackExchangePostsDb {

    @SneakyThrows
    public static void create(Path sqliteFile,
                       Path stackExchange7zFile) {
        if (Files.exists(sqliteFile))
            Files.delete(sqliteFile);
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

            var insertPost = connection.prepareStatement("""
                     INSERT INTO post(id, threadId, postYear, title, body, origSize, tags)
                     VALUES (?, ?, ?, ?, ?, ?, ?)
                     """);

            var iter = postReader.iterator();

            int cnt = 0;
            while (iter.hasNext()) {
                var post = iter.next();
                insertPost.setInt(1, post.id());

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

    @SneakyThrows
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
            TIntList threadIds = new TIntArrayList(10_000);
            ResultSet rs = selectThreadIds.executeQuery();

            while (rs.next()) {
                threadIds.add(rs.getInt(1));
            }

            System.out.println("Got " + threadIds.size() + " IDs");

            var idIterator = threadIds.iterator();
            int ordinal = 0;

            while (idIterator.hasNext()) {
                queryPostContents.setInt(1, idIterator.next());
                rs = queryPostContents.executeQuery();

                List<String> parts = new ArrayList<>();
                String title = "";
                int year = 2023;

                List<Future<String>> partWork = new ArrayList<>();
                var commonPool = ForkJoinPool.commonPool();
                while (rs.next()) {
                    String maybeTitle = rs.getString("title");

                    if (maybeTitle != null && !maybeTitle.isBlank())
                        title = maybeTitle;
                    int origSize = rs.getInt("origSize");

                    year = Math.min(year, rs.getInt("postYear"));

                    byte[] bytes = rs.getBytes("body");
                    partWork.add(commonPool.submit(
                            () -> new String(Zstd.decompress(bytes, origSize)
                    )));
                }

                for (var workItem : partWork) {
                    parts.add(workItem.get());
                }

                if (!consumer.test(new CombinedPostModel(ordinal++, title, year, parts)))
                    break;
            }

        }
        catch (SQLException ex) {
            ex.printStackTrace();
        }

    }

    public record CombinedPostModel(int ordinal,
                                    String title,
                                    int year,
                                    List<String> bodies)
    {

    }

}
