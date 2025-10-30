package nu.marginalia.model;

import nu.marginalia.util.QueryParams;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

public class EdgeUrl {
    public final String proto;
    public final EdgeDomain domain;
    public final Integer port;
    public final String path;
    public final String param;

    public EdgeUrl(String proto, EdgeDomain domain, Integer port, String path, String param) {
        this.proto = proto;
        this.domain = domain;
        this.port = port(port, proto);
        this.path = path;
        this.param = param;
    }

    public EdgeUrl(String url) throws URISyntaxException {
        this(parseURI(url));
    }

    private static URI parseURI(String url) throws URISyntaxException {
        try {
            return EdgeUriFactory.parseURILenient(url);
        } catch (URISyntaxException ex) {
            throw new URISyntaxException("Failed to parse URI '" + url + "'", ex.getMessage());
        }
    }

    public static Optional<EdgeUrl> parse(@Nullable String url) {
        try {
            if (null == url) {
                return Optional.empty();
            }

            return Optional.of(new EdgeUrl(url));
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }


    public EdgeUrl(URI URI) {
        try {
            String host = URI.getHost();

            if (host == null) { // deal with a rare serialization error
                host = "parse-error.invalid.example.com";
            }

            this.domain = new EdgeDomain(host);
            this.path = URI.getPath().isEmpty() ? "/" : URI.getPath();
            this.proto = URI.getScheme().toLowerCase();
            this.port = port(URI.getPort(), proto);
            this.param = QueryParams.queryParamsSanitizer(this.path, URI.getQuery());
        } catch (Exception ex) {
            System.err.println("Failed to parse " + URI);
            throw ex;
        }
    }

    public EdgeUrl(URL URL) {
        try {
            String host = URL.getHost();

            if (host == null) { // deal with a rare serialization error
                host = "parse-error.invalid.example.com";
            }

            this.domain = new EdgeDomain(host);
            this.path = URL.getPath().isEmpty() ? "/" : URL.getPath();
            this.proto = URL.getProtocol().toLowerCase();
            this.port = port(URL.getPort(), proto);
            this.param = QueryParams.queryParamsSanitizer(this.path, URL.getQuery());
        } catch (Exception ex) {
            System.err.println("Failed to parse " + URL);
            throw ex;
        }
    }

    private static Integer port(Integer port, String protocol) {
        if (null == port || port < 1) {
            return null;
        }
        if (protocol.equals("http") && port == 80) {
            return null;
        } else if (protocol.equals("https") && port == 443) {
            return null;
        }
        return port;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder(256);

        sb.append(proto);
        sb.append("://");
        sb.append(domain);

        if (port != null) {
            sb.append(':');
            sb.append(port);
        }

        EdgeUriFactory.urlencodePath(sb, path);

        if (param != null) {
            EdgeUriFactory.urlencodeQuery(sb, param);
        }

        return sb.toString();
    }


    public String toDisplayString() {
        StringBuilder sb = new StringBuilder(256);

        sb.append(proto);
        sb.append("://");
        sb.append(domain);

        if (port != null) {
            sb.append(':');
            sb.append(port);
        }

        sb.append(path);

        if (param != null) {
            sb.append('?').append(param);
        }

        return sb.toString();
    }

    public String dir() {
        return path.replaceAll("/[^/]+$", "/");
    }

    public String fileName() {
        return path.replaceAll(".*/", "");
    }

    public int depth() {
        return (int) path.chars().filter(c -> c == '/').count();
    }

    public EdgeUrl withPathAndParam(String path, String param) {
        return new EdgeUrl(proto, domain, port, path, param);
    }

    public EdgeUrl withProto(String newProto) {
        return new EdgeUrl(newProto, domain, port, path, param);
    }

    public boolean equals(Object other) {
        if (other == null) return false;
        if (other == this) return true;
        if (other instanceof EdgeUrl e) {
            return Objects.equals(e.domain, domain)
                    && Objects.equals(e.path, path)
                    && Objects.equals(e.param, param);
        }

        return true;
    }

    public boolean equalsExactly(Object other) {
        if (other == null) return false;
        if (other == this) return true;
        if (other instanceof EdgeUrl e) {
            return Objects.equals(e.proto, proto)
                    && Objects.equals(e.domain, domain)
                    && Objects.equals(e.port, port)
                    && Objects.equals(e.path, path)
                    && Objects.equals(e.param, param);
        }

        return true;
    }

    public int hashCode() {
        return Objects.hash(domain, path, param);
    }

    public URL asURL() throws MalformedURLException {
        try {
            return asURI().toURL();
        } catch (URISyntaxException e) {
            throw new MalformedURLException(e.getMessage());
        }
    }

    public URI asURI() throws URISyntaxException {
        if (port != null) {
            return new URI(this.proto, null, this.domain.toString(), this.port, this.path, this.param, null);
        }

        return new URI(this.proto, this.domain.toString(), this.path, this.param, null);
    }

    public EdgeDomain getDomain() {
        return this.domain;
    }

    public String getProto() {
        return this.proto;
    }

}

class EdgeUriFactory {
    public static URI parseURILenient(String url) throws URISyntaxException {

        if (shouldOmitUrlencodeRepair(url)) {
            try {
                return new URI(url);
            }
            catch (URISyntaxException ex) {
                // ignore and run the lenient parser
            }
        }

        var s = new StringBuilder(url.length()+8);

        int pathIdx = findPathIdx(url);
        if (pathIdx < 0) { // url looks like http://marginalia.nu
            return new URI(url + "/");
        }
        s.append(url, 0, pathIdx);

        // We don't want the fragment, and multiple fragments breaks the Java URIParser for some reason
        int end = url.indexOf("#");
        if (end < 0) end = url.length();

        int queryIdx = url.indexOf('?');
        if (queryIdx < 0) queryIdx = end;

        urlencodePath(s, url.substring(pathIdx, queryIdx));
        if (queryIdx < end) {
            urlencodeQuery(s, url.substring(queryIdx + 1, end));
        }
        return new URI(s.toString());
    }

    /** Break apart the path element of an URI into its components, and then
     * urlencode any component that needs it, and recombine it into a single
     * path element again.
     */
    public static void urlencodePath(StringBuilder sb, String path) {
        if (path == null || path.isEmpty()) {
            return;
        }

        String[] pathParts = StringUtils.split(path, '/');
        if (pathParts.length == 0) {
            sb.append('/');
            return;
        }

        boolean shouldUrlEncode = false;
        for (String pathPart : pathParts) {
            if (pathPart.isEmpty()) continue;

            if (needsUrlEncode(pathPart)) {
                shouldUrlEncode = true;
                break;
            }
        }

        for (String pathPart : pathParts) {
            if (pathPart.isEmpty()) continue;

            if (shouldUrlEncode) {
                sb.append('/');
                sb.append(URLEncoder.encode(pathPart, StandardCharsets.UTF_8).replace("+", "%20"));
            } else {
                sb.append('/');
                sb.append(pathPart);
            }
        }

        if (path.endsWith("/")) {
            sb.append('/');
        }

    }

    /** Break apart the query element of a URI into its components, and then
     * urlencode any component that needs it, and recombine it into a single
     * query element again.
     */
    public static void urlencodeQuery(StringBuilder sb, String param) {
        if (param == null || param.isEmpty()) {
            return;
        }

        String[] queryParts = StringUtils.split(param, '&');

        boolean shouldUrlEncode = false;
        for (String queryPart : queryParts) {
            if (queryPart.isEmpty()) continue;

            if (needsUrlEncode(queryPart)) {
                shouldUrlEncode = true;
                break;
            }
        }

        boolean first = true;
        for (String queryPart : queryParts) {
            if (queryPart.isEmpty()) continue;

            if (first) {
                sb.append('?');
                first = false;
            } else {
                sb.append('&');
            }

            if (shouldUrlEncode) {
                int idx = queryPart.indexOf('=');
                if (idx < 0) {
                    sb.append(URLEncoder.encode(queryPart, StandardCharsets.UTF_8));
                } else {
                    sb.append(URLEncoder.encode(queryPart.substring(0, idx), StandardCharsets.UTF_8));
                    sb.append('=');
                    sb.append(URLEncoder.encode(queryPart.substring(idx + 1), StandardCharsets.UTF_8));
                }
            } else {
                sb.append(queryPart);
            }
        }
    }

    /** Test if the url element needs URL encoding.
     * <p></p>
     * Note we may have been given an already encoded path element,
     * so we include % and + in the list of good characters
     */
    static boolean needsUrlEncode(String urlElement) {
        for (int i = 0; i < urlElement.length(); i++) {
            char c = urlElement.charAt(i);

            if (isUrlSafe(c)) continue;
            if ("+".indexOf(c) >= 0) continue;
            if (c == '%' && i + 2 < urlElement.length()) {
                char c1 = urlElement.charAt(i + 1);
                char c2 = urlElement.charAt(i + 2);
                if (isHexDigit(c1) && isHexDigit(c2)) {
                    i += 2;
                    continue;
                }
            }

            return true;
        }

        return false;
    }


    static boolean isUrlSafe(int c) {
        if (c >= 'a' && c <= 'z') return true;
        if (c >= 'A' && c <= 'Z') return true;
        if (c >= '0' && c <= '9') return true;
        if (c == '-' || c == '_' || c == '.' || c == '~') return true;

        return false;
    }

    /** Test if the URL is a valid URL that does not need to be
     * urlencoded.
     * <p></p>
     * This is a very simple heuristic test that does not guarantee
     * that the URL is valid, but it will identify cases where we
     * are fairly certain that the URL does not need encoding,
     * so we can skip a bunch of allocations and string operations
     * that would otherwise be needed to fix the URL.
     */
    static boolean shouldOmitUrlencodeRepair(String url) {
        int idx = 0;
        final int len = url.length();

        // Validate the scheme
        while (idx < len - 2) {
            char c = url.charAt(idx++);
            if (c == ':') break;
            if (!isAsciiAlphabetic(c)) return false;
        }
        if (url.charAt(idx++) != '/') return false;
        if (url.charAt(idx++) != '/') return false;

        // Validate the authority
        while (idx < len) {
            char c = url.charAt(idx++);
            if (c == '/') break;
            if (c == ':') continue;
            if (c == '@') continue;
            if (!isUrlSafe(c)) return false;
        }

        // Validate the path
        if (idx >= len) return true;

        while (idx < len) {
            char c = url.charAt(idx++);
            if (c == '?') break;
            if (c == '/') continue;
            if (c == '#') return true;
            if (!isUrlSafe(c)) return false;
        }

        if (idx >= len) return true;

        // Validate the query
        while (idx < len) {
            char c = url.charAt(idx++);
            if (c == '&') continue;
            if (c == '=') continue;
            if (c == '#') return true;
            if (!isUrlSafe(c)) return false;
        }

        return true;
    }


    private static boolean isAsciiAlphabetic(int c) {
        return (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static boolean isHexDigit(int c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    /** Find the index of the path element in a URL.
     * <p></p>
     * The path element starts after the scheme and authority part of the URL,
     * which is everything up to and including the first slash after the colon.
     */
    private static int findPathIdx(String url) throws URISyntaxException {
        int colonIdx = url.indexOf(':');
        if (colonIdx < 0 || colonIdx + 3 >= url.length()) {
            throw new URISyntaxException(url, "Lacking scheme");
        }
        return url.indexOf('/', colonIdx + 3);
    }


}