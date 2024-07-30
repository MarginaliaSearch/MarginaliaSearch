package nu.marginalia.integration.reddit;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class RedditEntryReaderTest {
    /** This test case exists for debugging, reddit sideloading.  It requires local reddit data,
     * and is not part of the normal test suite.  Update the path to a directory with reddit data
     * in the dbPath variable.
     * */
    Path dbPath = Path.of("/home/vlofgren/Code/RemoteEnv/local/index-1/uploads/reddit/");

    @Test
    void readSubmissions() throws IOException {
        if (!Files.exists(dbPath))
            return;

        try (var iter = RedditEntryReader.readSubmissions(dbPath.resolve("weightroom_submissions.zst"))) {
            for (int i = 0; iter.hasNext() && i<50; ) {
                var entry = iter.next();
                if (entry.selftext.length() > 1000 && entry.score > 10) {
                    System.out.println(entry);
                    i++;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void readComments() throws IOException {
        if (!Files.exists(dbPath))
            return;

        try (var iter = RedditEntryReader.readComments(dbPath.resolve("weightroom_comments.zst"))) {
            for (int i = 0; iter.hasNext() && i<50; ) {
                var entry = iter.next();
                if (entry.body.length() > 1000 && entry.score > 10 && entry.parent_id.startsWith("t1")) {
                    System.out.println(entry);
                    i++;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}