package nu.marginalia.util.tool;

import nu.marginalia.wmsa.edge.integration.wikipedia.WikipediaReader;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import org.jsoup.Jsoup;

import java.util.HashSet;
import java.util.Set;

public class WikipediaInternalLinkExtractorMain {
    public static void main(String... args) throws InterruptedException {
        new WikipediaReader(args[0], new EdgeDomain("en.wikipedia.org"), wikipediaArticle -> {


            var doc = Jsoup.parse(wikipediaArticle.body);
            String path = wikipediaArticle.url.path.substring("/wiki/".length());

            if (isIncluded(path)) {
                Set<String> seen = new HashSet<>(100);

                for (var atag : doc.getElementsByTag("a")) {
                    String href = atag.attr("href");

                    if (href.contains("#")) {
                        href = href.substring(0, href.indexOf('#'));
                    }

                    if (isIncluded(href) && href.length() > 2 && seen.add(href)) {
                        System.out.println(path + "\t" + href);
                    }
                }
            }

        }).join();
    }

    private static boolean isIncluded(String href) {
        return !href.contains(":")
            && !href.contains("/")
            && !href.contains("%")
            && !href.startsWith("#");
    }
}
