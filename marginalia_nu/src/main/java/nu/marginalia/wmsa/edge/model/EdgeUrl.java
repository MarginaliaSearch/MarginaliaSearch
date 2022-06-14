package nu.marginalia.wmsa.edge.model;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;

@Getter @Setter @Builder @EqualsAndHashCode
public class EdgeUrl implements WideHashable {
    public final String proto;
    public final EdgeDomain domain;
    public final Integer port;
    public final String path;

    public EdgeUrl(String proto, EdgeDomain domain, Integer port, String path) {
        this.proto = proto;
        this.domain = domain;
        this.port = port(port, proto);
        this.path = path;
    }

    public EdgeUrl(String url) throws URISyntaxException {
        this(new URI(urlencodeFixer(url)));
    }

    private static Pattern badCharPattern = Pattern.compile("[ \t\n\"<>\\[\\]()',|]");

    public static String urlencodeFixer(String url) throws URISyntaxException {
        var s = new StringBuilder();
        String goodChars = "&.?:/-;+$";
        String hexChars = "0123456789abcdefABCDEF";

        int pathIdx = findPathIdx(url);
        if (pathIdx < 0) {
            return url;
        }
        s.append(url, 0, pathIdx);

        for (int i = pathIdx; i < url.length(); i++) {
            int c = url.charAt(i);

            if (goodChars.indexOf(c) >= 0 || (c >= 'A' && c <='Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                s.appendCodePoint(c);
            }
            else if (c == '%' && i+2<url.length()) {
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
        this.domain = new EdgeDomain(URI.getHost());
        this.path = URI.getPath().isEmpty() ? "/" : URI.getPath();
        this.proto = URI.getScheme().toLowerCase();
        this.port = port(URI.getPort(), proto);
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
        String portPart = port == null ? "" : (":" + port);

        return proto + "://" + domain + portPart + "" + path;
    }

    public String dir() {
        return path.replaceAll("/[^/]+$", "/");
    }
    public String fileName() {
        return path.replaceAll(".*/", "");
    }

    public long wideHash() {
        long domainHash = domain.hashCode();
        long thisHash = hashCode();
        return (domainHash << 32) | thisHash;
    }

    public int depth() {
        return (int) path.chars().filter(c -> c=='/').count();
    }

    public EdgeUrl withPath(String s) {
        return new EdgeUrl(proto, domain, port, s);
    }
}
