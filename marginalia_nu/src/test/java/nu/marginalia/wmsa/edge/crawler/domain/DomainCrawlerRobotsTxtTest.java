package nu.marginalia.wmsa.edge.crawler.domain;

import crawlercommons.robots.SimpleRobotRules;
import crawlercommons.robots.SimpleRobotRulesParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class DomainCrawlerRobotsTxtTest {
    @Test
    public void testOverride() {
        String contentsStr = "User-agent: *\n" +
                "Disallow: /\n" +
                "\n" +
                "User-agent: Googlebot\n" +
                "User-agent: YandexBot\n" +
                "User-agent: Twitterbot\n" +
                "User-agent: special_archiver\n" +
                "User-agent: archive.org_bot\n" +
                "User-agent: search.marginalia.nu\n" +
                "Disallow:\n";

        byte[] contents = contentsStr.getBytes();
        SimpleRobotRules rules = new SimpleRobotRulesParser().parseContent("https://www.brutman.com/robots.txt",
                contents,
                "text/plain",
                "search.marginalia.nu");

        assertTrue(rules.isAllowed("http://www.brutman.com/test"));
    }
}