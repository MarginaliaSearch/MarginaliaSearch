package org.netpreserve.jwarc;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

/** Encapsulates out-of-band information about whether a website uses cookies,
 * using a non-standard WARC header "X-Has-Cookies".
 */
public class WarcXCookieInformationHeader {
    private boolean hasCookies = false;
    private static final String headerName = "X-Has-Cookies";

    public void update(OkHttpClient client, HttpUrl url) {
        if (!hasCookies) {
            hasCookies = !client.cookieJar().loadForRequest(url).isEmpty();
        }
    }

    public boolean hasCookies() {
        return hasCookies;
    }

    public void paint(WarcResponse.Builder builder) {
        builder.addHeader(headerName, hasCookies ? "1" : "0");
    }
    public void paint(WarcXResponseReference.Builder builder) {
        builder.addHeader(headerName, hasCookies ? "1" : "0");
    }

    public static boolean hasCookies(WarcRecord record) {
        return record.headers().contains(headerName, "1");
    }


}
