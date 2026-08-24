package nu.marginalia.dom;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

class DomPruningFilterTest {

    @Test
    public void testLinksArePreserved() throws IOException {
        String html = """
            <main class="readable">
            <article>
            <h1 class="p-name"><a href="https://kvibber.com/reviews/places/del-cerro/" class="u-url" rel="bookmark">Del Cerro Park</a></h1>
            
            <div class="e-content">
            
            
            <p class="stars"><abbr class="p-rating" value="4" title="4 stars out of 5" aria-label="4 stars out of 5.">★★★★☆</abbr></p>
            
            <p><a href="https://www.flickr.com/photos/kelsonv/albums/72157662146484291/">Incredible views</a> of the Pacific Ocean, Catalina Island, and the coastal hills and canyons from the top of the Palos Verdes Peninsula. <a href="https://journal.kvibber.com/2011/10/ocean-sunsets-beach-and-bluffs/">Calm and quiet, usually breezy</a>, with a few <a href="https://journal.kvibber.com/2015/06/hilltop-oceanview/">benches out near the edge</a>. High enough you can usually <a href="https://journal.kvibber.com/2012/05/solar-eclipse-festival/">get above the clouds</a>. Away from the edge there’s a large, flat lawn lined with trees, but no playground (much to my son’s <a href="https://journal.kvibber.com/2016/03/spring-sundogs-silhouettes/">disappointment</a> when he was younger).</p>
            </article>
            </main>
            """;

        var doc = Jsoup.parse(html,
                "https://kvibber.com/reviews/places/del-cerro/");

        doc.filter(new DomPruningFilter(0.5));
        
        String text = doc.text();
        System.out.println(text);

        Assertions.assertTrue(text.contains("Incredible views of the Pacific Ocean"));
    }
}