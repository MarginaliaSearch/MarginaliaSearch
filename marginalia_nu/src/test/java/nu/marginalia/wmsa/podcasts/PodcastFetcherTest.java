package nu.marginalia.wmsa.podcasts;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PodcastFetcherTest {

    @Test
    void fetchPodcast() {
        var result = new PodcastFetcher().fetchPodcast("hopwag", "https://rss.acast.com/readmeapoem");
        assertTrue(result.isPresent());
        System.out.println(result);
    }
}