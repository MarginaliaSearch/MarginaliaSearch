package nu.marginalia.wmsa.podcasts;

import com.google.common.escape.Escaper;
import com.google.common.net.PercentEscaper;
import lombok.Getter;
import nu.marginalia.wmsa.podcasts.model.*;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Getter
public class PodcastFetcher {

    private final List<PodcastEpisode> allEpisodes = new ArrayList<>();
    private final List<Podcast> allPodcasts = new ArrayList<>();
    private final Escaper urlEscaper = new PercentEscaper("", true);

    private final static Logger logger = LoggerFactory.getLogger(PodcastFetcher.class);

    private final DateTimeFormatter readableIsoDate =
             (new DateTimeFormatterBuilder()).parseCaseInsensitive().append(DateTimeFormatter.ISO_LOCAL_DATE).appendLiteral(' ').append(
                     DateTimeFormatter.ISO_LOCAL_TIME).toFormatter();

    public Optional<Podcast> fetchPodcast(String name, String url) {
        try {
            logger.info("Fetching podcast {} : {}", name, url);

            var doc = Jsoup.parse(new URL(url), 10_000);

            String title = doc.selectFirst("channel > title").text();
            String description = doc.selectFirst("channel > description").text();
            String link = doc.selectFirst("channel > link").text();

            var podcast = new Podcast(new PodcastMetadata(title, description, name, link));
            doc.getElementsByTag("item").forEach(item -> {
                try {
                    PodcastEpisode episode = fetchEpisode(name, title, item);
                    podcast.episodes.add(episode);
                    allEpisodes.add(episode);
                }
                catch (Exception ex) {
                    logger.error("Failed to fetch podcast episode", ex);
                }
            });

            allPodcasts.add(podcast);
            return Optional.of(podcast);
        } catch (IOException e) {
            logger.error("Failed to fetch podcast", e);
            return Optional.empty();
        }

    }

    @NotNull
    private PodcastEpisode fetchEpisode(String name, String title, org.jsoup.nodes.Element item) {
        String epTitle = item.getElementsByTag("title").text();
        String epGuid = name+":"+escapeUrlString(item.getElementsByTag("guid").text());
        String epDescription = item.getElementsByTag("description").text();
        String epPubDate = getPubDate(item);
        String epUrl = item.getElementsByTag("enclosure").attr("url");

        return new PodcastEpisode(name, title, epGuid, epTitle, epDescription, epPubDate, epUrl);
    }

    @NotNull
    private String getPubDate(Element item) {
        try {
            return ZonedDateTime.parse(item.getElementsByTag("pubDate").text(),
                    DateTimeFormatter.RFC_1123_DATE_TIME)
                    .format(readableIsoDate);
        }
        catch (Exception ex) {
            logger.error("Failed to parse date", ex);
            return item.getElementsByTag("pubDate").text();
        }
    }

    private String escapeUrlString(String s) {
        return urlEscaper.escape(s).replace("%", "_");
    }

    public PodcastNewEpisodes getNewEpisodes() {
        return new PodcastNewEpisodes(allEpisodes
                .stream()
                .sorted(Comparator.comparing(PodcastEpisode::getDateUploaded).reversed()).limit(10)
                .collect(Collectors.toList()));
    }

    public PodcastListing getListing() {
        final var metadatas = allPodcasts.stream()
                .map(Podcast::getMetadata)
                .sorted(Comparator.comparing(PodcastMetadata::getTitle))
                .collect(Collectors.toList());

        return
                new PodcastListing(metadatas);
    }
}
