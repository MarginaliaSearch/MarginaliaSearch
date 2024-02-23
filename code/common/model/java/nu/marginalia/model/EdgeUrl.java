package nu.marginalia.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import nu.marginalia.util.QueryParams;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

@Getter @Setter @Builder
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
            return new URI(urlencodeFixer(url));
        }
        catch (URISyntaxException ex) {
            throw new URISyntaxException(STR."Failed to parse URI '\{url}'", ex.getMessage());
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

    private static Pattern badCharPattern = Pattern.compile("[ \t\n\"<>\\[\\]()',|]");

    /* Java's URI parser is a bit too strict in throwing exceptions when there's an error.

       Here on the Internet, standards are like the picture on the box of the frozen pizza,
       and what you get is more like what's on the inside, we try to patch things instead,
       just give it a best-effort attempt att cleaning out broken or unnecessary constructions
       like bad or missing URLEncoding
     */
    public static String urlencodeFixer(String url) throws URISyntaxException {
        var s = new StringBuilder();
        String goodChars = "&.?:/-;+$#";
        String hexChars = "0123456789abcdefABCDEF";

        int pathIdx = findPathIdx(url);
        if (pathIdx < 0) { // url looks like http://marginalia.nu
            return url + "/";
        }
        s.append(url, 0, pathIdx);

        // We don't want the fragment, and multiple fragments breaks the Java URIParser for some reason
        int end = url.indexOf("#");
        if (end < 0) end = url.length();

        for (int i = pathIdx; i < end; i++) {
            int c = url.charAt(i);

            if (goodChars.indexOf(c) >= 0 || (c >= 'A' && c <='Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                s.appendCodePoint(c);
            }
            else if (c == '%' && i+2<end) {
                int cn = url.charAt(i+1);
                int cnn = url.charAt(i+2);
                if (hexChars.indexOf(cn) >= 0 && hexChars.indexOf(cnn) >= 0) {
                    s.appendCodePoint(c);
                }
                else {
                    s.append("%25");
                }
            }
            else {
                s.append(String.format("%%%02X", c));
            }
        }

        return s.toString();
    }

    private static int findPathIdx(String url) throws URISyntaxException {
        int colonIdx = url.indexOf(':');
        if (colonIdx < 0 || colonIdx + 2 >= url.length()) {
            throw new URISyntaxException(url, "Lacking protocol");
        }
        return url.indexOf('/', colonIdx+2);
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
        }
        catch (Exception ex) {
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
        }
        catch (Exception ex) {
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
        }
        else if (protocol.equals("https") && port == 443) {
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
        return (int) path.chars().filter(c -> c=='/').count();
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
        }
        catch (URISyntaxException e) {
            throw new MalformedURLException(e.getMessage());
        }
    }

    public URI asURI() throws URISyntaxException {
        if (port != null) {
            return new URI(this.proto, null, this.domain.toString(), this.port, this.path, this.param, null);
        }

        return new URI(this.proto, this.domain.toString(), this.path, this.param, null);
    }
}
