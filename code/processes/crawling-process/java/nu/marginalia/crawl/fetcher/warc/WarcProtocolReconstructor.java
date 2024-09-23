package nu.marginalia.crawl.fetcher.warc;

import okhttp3.Protocol;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/** We don't have access to the raw HTTP request and response, so we need to reconstruct them
 * as best is possible from the data we have available.
 */
public class WarcProtocolReconstructor {

    static String getHttpRequestString(String method,
                                       Map<String, List<String>> mainHeaders,
                                       Map<String, List<String>> extraHeaders,
                                       URI uri) {
        StringBuilder requestStringBuilder = new StringBuilder();

        final String encodedURL = encodeURLKeepSlashes(uri.getPath());

        requestStringBuilder.append(method).append(" ").append(encodedURL);

        if (uri.getQuery() != null) {
            requestStringBuilder.append("?").append(URLEncoder.encode(uri.getQuery(), StandardCharsets.UTF_8));
        }
        requestStringBuilder.append(" HTTP/1.1\r\n");
        requestStringBuilder.append("Host: ").append(uri.getHost()).append("\r\n");

        Set<String> addedHeaders = new HashSet<>();

        mainHeaders.forEach((k, values) -> {
            for (var value : values) {
                addedHeaders.add(k);
                requestStringBuilder.append(capitalizeHeader(k)).append(": ").append(value).append("\r\n");
            }
        });

        extraHeaders.forEach((k, values) -> {
            if (!addedHeaders.contains(k)) {
                for (var value : values) {
                    requestStringBuilder.append(capitalizeHeader(k)).append(": ").append(value).append("\r\n");
                }
            }
        });

        return requestStringBuilder.toString();
    }

    /** Java's URLEncoder will URLEncode slashes, which is not desirable
     * when sanitizing a URL for HTTP protocol purposes
     */

    private static String encodeURLKeepSlashes(String URL) {
        String[] parts = StringUtils.split(URL,"/");
        StringJoiner joiner = new StringJoiner("/");
        for (String part : parts) {
            joiner.add(URLEncoder.encode(part, StandardCharsets.UTF_8));
        }
        return joiner.toString();
    }

    static String getResponseHeader(String headersAsString, int code) {
        String version = "1.1";

        String statusCode = String.valueOf(code);
        String statusMessage = STATUS_CODE_MAP.getOrDefault(code, "Unknown");

        String headerString = getHeadersAsString(headersAsString);

        return "HTTP/" + version + " " + statusCode + " " + statusMessage + "\r\n" + headerString + "\r\n\r\n";
    }

    static String getResponseHeader(Response response, long size) {
        String version = response.protocol() == Protocol.HTTP_1_1 ? "1.1" : "2.0";

        String statusCode = String.valueOf(response.code());
        String statusMessage = STATUS_CODE_MAP.getOrDefault(response.code(), "Unknown");

        String headerString = getHeadersAsString(response, size);

        return "HTTP/" + version + " " + statusCode + " " + statusMessage + "\r\n" + headerString + "\r\n\r\n";
    }

    private static final Map<Integer, String> STATUS_CODE_MAP = Map.ofEntries(
            Map.entry(200, "OK"),
            Map.entry(201, "Created"),
            Map.entry(202, "Accepted"),
            Map.entry(203, "Non-Authoritative Information"),
            Map.entry(204, "No Content"),
            Map.entry(205, "Reset Content"),
            Map.entry(206, "Partial Content"),
            Map.entry(207, "Multi-Status"),
            Map.entry(208, "Already Reported"),
            Map.entry(226, "IM Used"),
            Map.entry(300, "Multiple Choices"),
            Map.entry(301, "Moved Permanently"),
            Map.entry(302, "Found"),
            Map.entry(303, "See Other"),
            Map.entry(304, "Not Modified"),
            Map.entry(307, "Temporary Redirect"),
            Map.entry(308, "Permanent Redirect"),
            Map.entry(400, "Bad Request"),
            Map.entry(401, "Unauthorized"),
            Map.entry(403, "Forbidden"),
            Map.entry(404, "Not Found"),
            Map.entry(405, "Method Not Allowed"),
            Map.entry(406, "Not Acceptable"),
            Map.entry(408, "Request Timeout"),
            Map.entry(409, "Conflict"),
            Map.entry(410, "Gone"),
            Map.entry(411, "Length Required"),
            Map.entry(412, "Precondition Failed"),
            Map.entry(413, "Payload Too Large"),
            Map.entry(414, "URI Too Long"),
            Map.entry(415, "Unsupported Media Type"),
            Map.entry(416, "Range Not Satisfiable"),
            Map.entry(417, "Expectation Failed"),
            Map.entry(418, "I'm a teapot"),
            Map.entry(421, "Misdirected Request"),
            Map.entry(426, "Upgrade Required"),
            Map.entry(428, "Precondition Required"),
            Map.entry(429, "Too Many Requests"),
            Map.entry(431, "Request Header Fields Too Large"),
            Map.entry(451, "Unavailable For Legal Reasons"),
            Map.entry(500, "Internal Server Error"),
            Map.entry(501, "Not Implemented"),
            Map.entry(502, "Bad Gateway"),
            Map.entry(503, "Service Unavailable"),
            Map.entry(504, "Gateway Timeout"),
            Map.entry(505, "HTTP Version Not Supported"),
            Map.entry(506, "Variant Also Negotiates"),
            Map.entry(507, "Insufficient Storage"),
            Map.entry(508, "Loop Detected"),
            Map.entry(510, "Not Extended"),
            Map.entry(511, "Network Authentication Required")
    );

    static private String getHeadersAsString(String headersBlob) {
        StringJoiner joiner = new StringJoiner("\r\n");

        Arrays.stream(headersBlob.split("\n")).forEach(joiner::add);

        return joiner.toString();
    }

    static private String getHeadersAsString(Response response, long responseSize) {
        StringJoiner joiner = new StringJoiner("\r\n");

        response.headers().toMultimap().forEach((k, values) -> {
            String headerCapitalized = capitalizeHeader(k);

            // Omit pseudoheaders injected by the crawler itself
            if (headerCapitalized.startsWith("X-Marginalia"))
                return;

            // Omit Transfer-Encoding and Content-Encoding headers
            if (headerCapitalized.equals("Transfer-Encoding"))
                return;
            if (headerCapitalized.equals("Content-Encoding"))
                return;

            // Since we're transparently decoding gzip, we need to update the Content-Length header
            // to reflect the actual size of the response body. We'll do this at the end.
            if (headerCapitalized.equals("Content-Length"))
                return;

            for (var value : values) {
                joiner.add(headerCapitalized + ": " + value);
            }
        });

        joiner.add("Content-Length: " + responseSize);

        return joiner.toString();
    }

    // okhttp gives us flattened headers, so we need to reconstruct Camel-Kebab-Case style
    // for the WARC parser's sake...
    static private String capitalizeHeader(String k) {
        return Arrays.stream(StringUtils.split(k, '-'))
                .map(StringUtils::capitalize)
                .collect(Collectors.joining("-"));
    }

}
