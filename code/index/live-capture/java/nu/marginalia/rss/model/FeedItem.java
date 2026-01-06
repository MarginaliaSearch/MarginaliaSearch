package nu.marginalia.rss.model;

import nu.marginalia.rss.svc.SimpleFeedParser;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;

import java.net.URI;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public record FeedItem(String title,
                       String date,
                       String description,
                       String url) implements Comparable<FeedItem>
{
    public static final int MAX_DESC_LENGTH = 255;
    public static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    public static FeedItem fromItem(SimpleFeedParser.ItemData item, boolean keepFragment) {
        String title = item.title();
        String date = getItemDate(item);
        String description = getItemDescription(item);
        String url;

        if (keepFragment) {
            url = item.url();
        }
        else {
            try {
                String link = item.url();
                var linkUri = new URI(link);
                var cleanUri = new URI(linkUri.getScheme(), linkUri.getAuthority(), linkUri.getPath(), linkUri.getQuery(), null);
                url = cleanUri.toString();
            }
            catch (Exception e) {
                // fallback to original link if we can't clean it, this is not a very important step
                url = item.url();
            }
        }

        return new FeedItem(title, date, description, url);
    }

    private static String getItemDescription(SimpleFeedParser.ItemData item) {
        String rawDescription = item.description();
        if (rawDescription.indexOf('<') >= 0) {
            rawDescription = Jsoup.parseBodyFragment(rawDescription).text();
        }

        return StringUtils.truncate(rawDescription, MAX_DESC_LENGTH);
    }

    // e.g. http://fabiensanglard.net/rss.xml does dates like this:  1 Apr 2021 00:00:00 +0000
    private static final DateTimeFormatter extraFormatter = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss Z");
    private static String getItemDate(SimpleFeedParser.ItemData item) {
        Optional<ZonedDateTime> zonedDateTime = Optional.empty();
        try {
            zonedDateTime = item.getPubDateZonedDateTime();
        }
        catch (Exception e) {
            try {
                zonedDateTime = Optional.of(ZonedDateTime.from(extraFormatter.parse(item.pubDate())));
            }
            catch (Exception e2) {
                // ignore
            }
        }

        return zonedDateTime.map(date -> date.format(DATE_FORMAT)).orElse("");
    }

    public ZonedDateTime getUpdateTimeZD() {
        return ZonedDateTime.parse(date, DATE_FORMAT);
    }


    @Override
    public int compareTo(@NotNull FeedItem o) {
        return o.date.compareTo(date);
    }
}
