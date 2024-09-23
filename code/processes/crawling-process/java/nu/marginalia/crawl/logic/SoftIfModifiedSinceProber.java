package nu.marginalia.crawl.logic;

import com.google.common.base.Strings;
import nu.marginalia.crawl.fetcher.ContentTags;
import nu.marginalia.model.EdgeUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Objects;

public class SoftIfModifiedSinceProber {

    private final String userAgentString;
    private final OkHttpClient client;

    public SoftIfModifiedSinceProber(String userAgentString, OkHttpClient httpClient) {
        this.userAgentString = userAgentString;
        this.client = httpClient;
    }

    /** Implement a soft probe of the last modified time of the given URL with a HEAD request.
     * This is used to detect if the URL has been modified since the last time we crawled it.
     */
    public boolean probeModificationTime(EdgeUrl url, ContentTags tags) throws IOException  {
        var headBuilder = new Request.Builder().head()
                .addHeader("User-agent", userAgentString)
                .addHeader("Accept-Encoding", "gzip")
                .url(url.toString());

        // This logic is only applicable if we only have a last-modified time, but no ETag.
        if (Strings.isNullOrEmpty(tags.lastMod()))
            return false;
        if (!Strings.isNullOrEmpty(tags.etag()))
            return false;

        var head = headBuilder.build();
        var call = client.newCall(head);

        try (var rsp = call.execute()) {
            if (rsp.code() != 200) {
                return false;
            }

            var contentTypeHeader = rsp.header("Last-Modified");
            return Objects.equals(contentTypeHeader, tags.lastMod());
        }
        catch (SocketTimeoutException e) { // suppress timeout exceptions to reduce log noise
            return false;
        }
    }

}
