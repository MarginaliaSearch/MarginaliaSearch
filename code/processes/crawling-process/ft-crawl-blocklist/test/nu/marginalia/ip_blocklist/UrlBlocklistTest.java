package nu.marginalia.ip_blocklist;

import nu.marginalia.model.EdgeUrl;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlBlocklistTest {

    @Test
    void isUrlBlocked() throws URISyntaxException {
        UrlBlocklist blocklist = new UrlBlocklist();
        assertTrue(blocklist.isUrlBlocked(new EdgeUrl("https://memex.marginalia.nu/ghc/ghc/blob/1b1067d14b656bbbfa7c47f156ec2700c9751549/compiler/main/UpdateCafInfos.hs")));
        assertTrue(blocklist.isUrlBlocked(new EdgeUrl("http://www.marginalia.nu/wp-content/uploads/test.jpg")));
        assertTrue(blocklist.isUrlBlocked(new EdgeUrl("http://yelenasimone.com/pdf/download-a-course-in-algebra.html")));
        assertFalse(blocklist.isUrlBlocked(new EdgeUrl("http://yelenasimone.com/nope/x-a-course-in-algebra.html")));
        assertTrue(blocklist.isUrlBlocked(new EdgeUrl("http://yelenasimone.com/_module/slide/pqPan/library/american-sour-beer-innovative-techniques-for-mixed-fermentations/")));
        assertTrue(blocklist.isUrlBlocked(new EdgeUrl("http://w-m-p.de/images/book/download-firstborn-starcraft-dark-templar-book-1.php")));
        assertTrue(blocklist.isUrlBlocked(new EdgeUrl("https://sqlite.org/src/info/6376abec766e9a0785178b1823b5a587e9f1ccbc")));
    }
}