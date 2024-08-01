package nu.marginalia.integration.reddit.db;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

class RedditDbTest {

    @Disabled
    @Test
    void create() throws SQLException, IOException {
        RedditDb.create(
                Path.of("/home/vlofgren/Exports/reddit/weightroom_submissions.zst"),
                Path.of("/home/vlofgren/Exports/reddit/weightroom_comments.zst"),
                Path.of("/tmp/reddit.db")
        );
    }

    @Disabled
    @Test
    void readSubmissions() {
        try (var iter = RedditDb.getSubmissions(Path.of("/home/vlofgren/Code/RemoteEnv/local/index-1/uploads/reddit/weightroom.8eda94e.db"))) {
            for (int i = 0; i < 10; i++) {
                System.out.println(iter.next());
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Disabled
    @Test
    void readComments() {
        try (var iter = RedditDb.getComments(Path.of("/home/vlofgren/Code/RemoteEnv/local/index-1/uploads/reddit/weightroom.8eda94e.db"))) {
            for (int i = 0; i < 10; i++) {
                var entry = iter.next();
                System.out.println(iter.next());
                System.out.println(LocalDate.ofInstant(Instant.ofEpochSecond(entry.created_utc), ZoneOffset.UTC).getYear());
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}