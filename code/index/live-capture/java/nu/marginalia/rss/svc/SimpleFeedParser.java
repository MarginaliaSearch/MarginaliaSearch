package nu.marginalia.rss.svc;

import com.apptasticsoftware.rssreader.DateTimeParser;
import com.apptasticsoftware.rssreader.util.Default;
import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SimpleFeedParser {

    private static final DateTimeParser dateTimeParser = Default.getDateTimeParser();

    public record ItemData (
            String title,
            String description,
            String url,
            String pubDate
    ) {
        public boolean isWellFormed() {
            return title != null && !title.isBlank() &&
                    description != null && !description.isBlank() &&
                    url != null && !url.isBlank() &&
                    pubDate != null && !pubDate.isBlank();
        }

        public Optional<ZonedDateTime> getPubDateZonedDateTime() {
            try {
                return Optional.ofNullable(dateTimeParser.parse(pubDate()));
            }
            catch (Exception e) {
                return Optional.empty();
            }
        }

    }

    public static List<ItemData> parse(String content) {
        var doc = Jsoup.parse(content, Parser.xmlParser());
        List<ItemData> ret = new ArrayList<>();

        doc.select("item, entry").forEach(element -> {
            String link = "";
            String title = "";
            String description = "";
            String pubDate = "";

            for (String attr : List.of("title", "dc:title")) {
                if (!title.isBlank())
                    break;
                var tag = element.getElementsByTag(attr).first();
                if (tag != null) {
                    title = tag.text();
                }
            }

            for (String attr : List.of("title", "summary", "content", "description", "dc:description")) {
                if (!description.isBlank())
                    break;
                var tag = element.getElementsByTag(attr).first();
                if (tag != null) {
                    description = tag.text();
                }
            }

            for (String attr : List.of("pubDate", "published", "updated", "issued", "created", "dc:date")) {
                if (!pubDate.isBlank())
                    break;
                var tag = element.getElementsByTag(attr).first();
                if (tag != null) {
                    pubDate = tag.text();
                }
            }

            for (String attr : List.of("link", "url")) {
                if (!link.isBlank())
                    break;
                var tag = element.getElementsByTag(attr).first();

                if (tag != null) {
                    String linkText = tag.text();

                    if (linkText.isBlank()) {
                        linkText = tag.attr("href");
                    }

                    link = linkText;
                }

            }

            ret.add(new ItemData(title, description, link, pubDate));
        });


        return ret;
    }

}
