package nu.marginalia.model;

import nu.marginalia.util.QueryParams;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

public class EdgeUrl implements Serializable {
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
            return EdgeUriFactory.uriFromString(url);
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

        sb.append(path);

        if (param != null) {
            sb.append('?');
            sb.append(param);
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

/* Java's URI parser is a bit too strict in throwing exceptions when there's an error.

   Here on the Internet, standards are like the picture on the box of the frozen pizza,
   and what you get is more like what's on the inside, we try to patch things instead,
   just give it a best-effort attempt att cleaning out broken or unnecessary constructions
   like bad or missing URLEncoding
 */
class EdgeUriFactory {
    public static URI uriFromString(String url) throws URISyntaxException {
        var s = new StringBuilder();

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

        recombinePaths(s, url.substring(pathIdx, queryIdx));
        if (queryIdx < end) {
            recombineQueryString(s, url.substring(queryIdx + 1, end));
        }
        return new URI(s.toString());
    }

    private static void recombinePaths(StringBuilder sb, String path) {
        if (path == null || path.isEmpty()) {
            return;
        }

        String[] pathParts = StringUtils.split(path, '/');
        if (pathParts.length == 0) {
            sb.append('/');
            return;
        }

        for (String pathPart : pathParts) {
            if (pathPart.isEmpty()) continue;

            if (needsUrlEncode(pathPart)) {
                sb.append('/');
                sb.append(URLEncoder.encode(pathPart, StandardCharsets.UTF_8));
            } else {
                sb.append('/');
                sb.append(pathPart);
            }
        }

    }

    private static void recombineQueryString(StringBuilder sb, String param) {
        if (param == null || param.isEmpty()) {
            return;
        }

        sb.append('?');
        String[] pathParts = StringUtils.split(param, '&');
        boolean first = true;
        for (String pathPart : pathParts) {
            if (pathPart.isEmpty()) continue;

            if (first) {
                first = false;
            } else {
                sb.append('&');
            }
            if (needsUrlEncode(pathPart)) {
                sb.append(URLEncoder.encode(pathPart, StandardCharsets.UTF_8));
            } else {
                sb.append(pathPart);
            }
        }
    }


    /** Test if the url element needs URL encoding.
     * <p></p>
     * Note we may have been given an already encoded path element,
     * so we include % and + in the list of good characters
     */
    private static boolean needsUrlEncode(String urlElement) {
        for (int i = 0; i < urlElement.length(); i++) {
            char c = urlElement.charAt(i);

            if (c >= 'a' && c <= 'z') continue;
            if (c >= 'A' && c <= 'Z') continue;
            if (c >= '0' && c <= '9') continue;
            if ("-_.~+?=&".indexOf(c) >= 0) continue;
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

    private static boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static int findPathIdx(String url) throws URISyntaxException {
        int colonIdx = url.indexOf(':');
        if (colonIdx < 0 || colonIdx + 3 >= url.length()) {
            throw new URISyntaxException(url, "Lacking protocol");
        }
        return url.indexOf('/', colonIdx + 3);
    }


}