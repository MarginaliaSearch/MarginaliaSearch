package nu.marginalia.crawl.logic;

import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.body.ContentTypeLogic;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InterruptedIOException;
import java.util.Objects;

public class ContentTypeProber {

    private static final Logger logger = LoggerFactory.getLogger(ContentTypeProber.class);
    private final String userAgentString;
    private final OkHttpClient client;
    private final ContentTypeLogic contentTypeLogic = new ContentTypeLogic();

    public ContentTypeProber(String userAgentString, OkHttpClient httpClient) {
        this.userAgentString = userAgentString;
        this.client = httpClient;
    }

    /** Probe the content type of the given URL with a HEAD request.
     * This is used to detect binary files, which we don't want to crawl.
     * <p>
     * If the URL redirects, the final URL is returned, to avoid redundant
     * requests.
     *
     * @param url The URL to probe
     * @return A ContentTypeProbeResult
     */
    public ContentTypeProbeResult probeContentType(EdgeUrl url) {
        logger.debug("Probing suspected binary {}", url);

        var headBuilder = new Request.Builder().head()
                .addHeader("User-agent", userAgentString)
                .addHeader("Accept-Encoding", "gzip")
                .url(url.toString());

        var head = headBuilder.build();
        var call = client.newCall(head);

        try (var rsp = call.execute()) {
            var contentTypeHeader = rsp.header("Content-type");

            if (contentTypeHeader != null && !contentTypeLogic.isAllowableContentType(contentTypeHeader)) {
                return new ContentTypeProbeResult.BadContentType(contentTypeHeader, rsp.code());
            }

            // Update the URL to the final URL of the HEAD request, otherwise we might end up doing

            // HEAD 301 url1 -> url2
            // HEAD 200 url2
            // GET 301 url1 -> url2
            // GET 200 url2

            // which is not what we want. Overall we want to do as few requests as possible to not raise
            // too many eyebrows when looking at the logs on the target server.  Overall it's probably desirable
            // that it looks like the traffic makes sense, as opposed to looking like a broken bot.

            var redirectUrl = new EdgeUrl(rsp.request().url().toString());
            EdgeUrl ret;

            if (Objects.equals(redirectUrl.domain, url.domain)) ret = redirectUrl;
            else ret = url;

            return new ContentTypeProbeResult.Ok(ret);

        } catch (InterruptedIOException ex) {
            return new ContentTypeProbeResult.Timeout(ex);
        } catch (Exception ex) {
            logger.error("Error during fetching {}[{}]", ex.getClass().getSimpleName(), ex.getMessage());

            return new ContentTypeProbeResult.Exception(ex);
        }
    }

    public sealed interface ContentTypeProbeResult {
        record Ok(EdgeUrl resolvedUrl) implements ContentTypeProbeResult { }
        record BadContentType(String contentType, int statusCode) implements ContentTypeProbeResult { }
        record Timeout(java.lang.Exception ex) implements ContentTypeProbeResult { }
        record Exception(java.lang.Exception ex) implements ContentTypeProbeResult { }
    }
}
