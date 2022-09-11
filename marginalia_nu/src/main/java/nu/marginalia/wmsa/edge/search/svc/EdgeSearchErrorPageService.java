package nu.marginalia.wmsa.edge.search.svc;

import com.google.inject.Inject;
import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.edge.index.client.EdgeIndexClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Response;

public class EdgeSearchErrorPageService {
    private final EdgeIndexClient indexClient;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public EdgeSearchErrorPageService(EdgeIndexClient indexClient) {
        this.indexClient = indexClient;
    }

    public void serveError(Context ctx, Response rsp) {
        boolean isIndexUp = indexClient.isAlive();

        try {
            if (!isIndexUp) {
                rsp.body(renderError("The index is down",
                        """
                            The search index server appears to be down.
                            <p>
                            The server was possibly restarted to bring online some changes.
                            Restarting the index typically takes a few minutes, during which
                            searches can't be served.
                        """));
            } else if (indexClient.isBlocked(ctx).blockingFirst()) {
                rsp.body(renderError("The index is starting up",
                        """
                            The search index server appears to be in the process of starting up.
                            This typically takes a few minutes. Be patient.
                        """));
            }
            else {
                rsp.body(renderError("Error processing request",
                        """
                            The search index appears to be up and running, so the problem may be related
                            to some wider general error, or pertain to an error handling your query.
                        """));
            }
        }
        catch (Exception ex) {
            rsp.body(renderError("Error processing error",
                    """
                        An error has occurred, additionally, an error occurred while handling that error
                        <p>
                        <a href="https://www.youtube.com/watch?v=dsx2vdn7gpY">https://www.youtube.com/watch?v=dsx2vdn7gpY</a>.
                        
                    """));
        }
    }

    private String renderError(String title, String message) {
        return """
                <!DOCTYPE html>
                <title>Error</title>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <link rel="stylesheet" href="https://search.marginalia.nu/style-new.css">
                <header>
                    <nav>
                        <a href="https://www.marginalia.nu/">Marginalia</a>
                        <a href="https://memex.marginalia.nu/projects/edge/about.gmi">About</a>
                        <a href="https://memex.marginalia.nu/projects/edge/supporting.gmi">Support</a>
                    </nav>
                </header>
                <article>
                    <form method="get" action="/search">
                        <section class="search-box">
                            <h1>Search the Internet</h1>
                            <div class="input">
                                <input id="query" name="query" placeholder="Search terms" value="" autocomplete="off">
                                <input value="Go" type="submit">
                            </div>
                            <div class="settings">
                                <select name="profile" id="profile">
                                    <option value="default">Popular Sites</option>
                                    <option value="modern">Blogs and Personal Websites</option>
                                    <option value="academia">Academia, Forums, Big Websites</option>
                                    <option value="yolo">Default Ranking Algorithm</option>
                                    <option value="food">Recipes 🍳</option>
                                    <option value="corpo">Experimental</option>
                                </select>
                                <select name="js" id="js">
                                    <option value="default">Allow JS</option>
                                    <option value="no-js">Deny JS</option>
                                    <option value="yes-js">Require JS</option>
                                </select>
                            </div>
                            <div class="extra">
                                <a href="https://search.marginalia.nu/explore/random">Random Websites</a>
                            </div>
                        </section>
                    </form>
                <div class="cards big">
                    <div class="card problems">
                    <h2>
                """
                + title +
                """
                    </h2>
                    <div class="info">
                """
                +message+
                """
                    </div>
                </div>
                <div class="card">
                <h2>More Info</h2>
                <div class="info">
                You may be able to find more information here:
                <ul>
                  <li><a href="https://status.marginalia.nu/">Maintenance Messages</a></li>
                  <li><a href="https://twitter.com/MarginaliaNu">Twitter Account</a></li>
                  <li>Email Me: <tt>kontakt@marginalia.nu</tt></li>
                </ul>
                </div>
                </div>
                """;
    }
}
