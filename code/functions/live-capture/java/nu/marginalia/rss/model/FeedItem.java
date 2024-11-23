package nu.marginalia.rss.model;

import com.apptasticsoftware.rssreader.Item;
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

    public static FeedItem fromItem(Item item, boolean keepFragment) {
        String title = item.getTitle().orElse("");
        String date = getItemDate(item);
        String description = getItemDescription(item);
        String url;

        if (keepFragment || item.getLink().isEmpty()) {
            url = item.getLink().orElse("");
        }
        else {
            try {
                String link = item.getLink().get();
                var linkUri = new URI(link);
                var cleanUri = new URI(linkUri.getScheme(), linkUri.getAuthority(), linkUri.getPath(), linkUri.getQuery(), null);
                url = cleanUri.toString();
            }
            catch (Exception e) {
                // fallback to original link if we can't clean it, this is not a very important step
                url = item.getLink().get();
            }
        }

        return new FeedItem(title, date, description, url);
    }

    private static String getItemDescription(Item item) {
        Optional<String> description = item.getDescription();
        if (description.isEmpty())
            return "";

        String rawDescription = description.get();
        if (rawDescription.indexOf('<') >= 0) {
            rawDescription = Jsoup.parseBodyFragment(rawDescription).text();
        }

        return StringUtils.truncate(rawDescription, MAX_DESC_LENGTH);
    }

    // e.g. http://fabiensanglard.net/rss.xml does dates like this:  1 Apr 2021 00:00:00 +0000
    private static final DateTimeFormatter extraFormatter = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss Z");
    private static String getItemDate(Item item) {
        Optional<ZonedDateTime> zonedDateTime = Optional.empty();
        try {
            zonedDateTime = item.getPubDateZonedDateTime();
        }
        catch (Exception e) {
            zonedDateTime = item.getPubDate()
                    .map(extraFormatter::parse)
                    .map(ZonedDateTime::from);
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
