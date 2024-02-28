package nu.marginalia.crawling;

import nu.marginalia.link_parser.LinkParser;
import nu.marginalia.model.EdgeUrl;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

class RssCrawlerTest {

    final LinkParser lp = new LinkParser();

    @Test @Disabled
    public void test() throws URISyntaxException, IOException {
        getLinks(new EdgeUrl("https://eli.li/feed.rss"), new String(Files.readAllBytes(Path.of("/home/vlofgren/Work/feed.rss"))));
    }

    private Set<EdgeUrl> getLinks(EdgeUrl base, String str) {

        var doc = Jsoup.parse(str.replaceAll("link", "lnk"));

        Set<EdgeUrl> urls = new LinkedHashSet<>();

        doc.select("entry > lnk[rel=alternate]").forEach(element -> {
            var href = element.attr("href");
            if (href != null && !href.isBlank()) {
                lp.parseLink(base, href)
                        .filter(u -> Objects.equals(u.domain.topDomain, base.domain.topDomain))
                        .ifPresent(urls::add);
            }
        });

        doc.getElementsByTag("lnk").forEach(element -> {
            var href = element.text();
            if (href != null && !href.isBlank()) {
                lp.parseLink(base, href)
                        .filter(u -> Objects.equals(u.domain.topDomain, base.domain.topDomain))
                        .ifPresent(urls::add);
            }
        });

        doc.select("item > guid[isPermalink=true]").forEach(element -> {
            var href = element.text();
            if (href != null && !href.isBlank()) {
                lp.parseLink(base, href)
                        .filter(u -> Objects.equals(u.domain.topDomain, base.domain.topDomain))
                        .ifPresent(urls::add);
            }
        });

        return urls;
    }

}