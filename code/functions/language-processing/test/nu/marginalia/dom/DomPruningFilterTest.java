package nu.marginalia.dom;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

class DomPruningFilterTest {

    @Test
    public void testNonContentElements() {
        var doc = Jsoup.parse("""
                <main>
                  <article><p>An article about <code>&lt;dialog&gt;</code> elements.</p></article>
                  <dialog><p>Closed dialog content.</p></dialog>
                  <dialog open><p>Open dialog content.</p></dialog>
                  <details-dialog><p>Custom dialog content.</p></details-dialog>
                  <template><article><p>Template content.</p></article></template>
                  <script>window.message = 'Script content';</script>
                  <style>.message::before { content: 'Style content'; }</style>
                  <menu><li>Menu content.</li></menu>
                  <details><summary>More information</summary><p>Article details.</p></details>
                </main>
                """);

        doc.body().filter(new DomPruningFilter(0.5));

        Assertions.assertEquals("An article about <dialog> elements. More information Article details.",
                doc.body().text());
        Assertions.assertTrue(doc.select("dialog, details-dialog, template, script, style, menu").isEmpty());
    }

    @Test
    public void testHiddenAttribute() {
        var doc = Jsoup.parse("""
                <main>
                  <article><p>The repository README is available.</p></article>
                  <div id="ajax-error-message" class="ajax-error-message flash flash-error" hidden>
                    You can’t perform that action at this time.
                  </div>
                  <include-fragment>
                    <div data-show-on-forbidden-error hidden>
                      <h3>Uh oh!</h3>
                      <p>There was an error while loading. Please reload this page.</p>
                    </div>
                  </include-fragment>
                  <div hidden="hidden"><p>Another hidden fallback.</p></div>
                  <div hidden="false"><p>The hidden attribute still hides this.</p></div>
                  <div><p>A visible error message should remain.</p></div>
                </main>
                """);

        doc.body().filter(new DomPruningFilter(0.5));

        Assertions.assertEquals("The repository README is available. A visible error message should remain.",
                doc.body().text());
    }

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
