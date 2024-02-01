package nu.marginalia.link_parser;

import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import lombok.SneakyThrows;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.util.QueryParams;
import org.jetbrains.annotations.Contract;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public class LinkParser {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    // These are schemas that we don't want to try to index
    private final List<String> blockedSchemaList = List.of(
            "mailto:", "javascript:", "tel:", "itpc:", "#", "file:");

    // These are file suffixes we suspect may be a binary file
    private final List<String> binarySuffixList = List.of(
            ".pdf", ".mp3", ".wmv", ".avi", ".zip", ".7z",
            ".mpv", ".mp4", ".avi", ".mkv", ".tiff", ".dat", ".tar",
            ".com", ".bat", ".sh",
            ".bin", ".exe", ".tar.gz", ".tar.bz2", ".xml", ".swf",
            ".wav", ".ogg", ".jpg", ".jpeg", ".png", ".gif", ".webp",
            ".webm", ".bmp", ".doc", ".docx", ".ppt", ".pptx", ".xls", ".xlsx",
            ".gz", ".asc", ".md5", ".asf", ".mov", ".sig", ".pub", ".iso");

    @Contract(pure=true)
    public Optional<EdgeUrl> parseLink(EdgeUrl relativeBaseUrl, Element l) {
        return Optional.of(l)
                .filter(this::shouldIndexLink)
                .map(this::getUrl)
                .map(link -> resolveRelativeUrl(relativeBaseUrl, link))
                .flatMap(this::createURI)
                .map(URI::normalize)
                .map(this::renormalize)
                .flatMap(this::createEdgeUrl);
    }

    @Contract(pure=true)
    public Optional<EdgeUrl> parseLinkPermissive(EdgeUrl relativeBaseUrl, Element l) {
        return Optional.of(l)
                .map(this::getUrl)
                .map(link -> resolveRelativeUrl(relativeBaseUrl, link))
                .flatMap(this::createURI)
                .map(URI::normalize)
                .map(this::renormalize)
                .flatMap(this::createEdgeUrl);
    }

    private Optional<URI> createURI(String s) {
        try {
            return Optional.of(new URI(s));
        }
        catch (URISyntaxException e) {
            logger.debug("Bad URI {}", s);
            return Optional.empty();
        }
    }

    private Optional<EdgeUrl> createEdgeUrl(URI uri) {
        try {
            return Optional.of(new EdgeUrl(uri));
        }
        catch (Exception ex) {
            logger.debug("Bad URI {}", uri);
            return Optional.empty();
        }
    }

    @Contract(pure=true)
    public Optional<EdgeUrl> parseLink(EdgeUrl baseUrl, String str) {
        return Optional.of(str)
                .map(link -> resolveRelativeUrl(baseUrl, link))
                .flatMap(this::createURI)
                .map(URI::normalize)
                .map(this::renormalize)
                .flatMap(this::createEdgeUrl);
    }

    @Contract(pure=true)
    public Optional<EdgeUrl> parseFrame(EdgeUrl baseUrl, Element frame) {
        return Optional.of(frame)
                .map(l -> l.attr("src"))
                .map(link -> resolveRelativeUrl(baseUrl, link))
                .flatMap(this::createURI)
                .map(URI::normalize)
                .map(this::renormalize)
                .flatMap(this::createEdgeUrl);
    }

    @Contract(pure=true)
    public Optional<EdgeUrl> parseMetaRedirect(EdgeUrl baseUrl, Element meta) {
        return Optional.of(meta)
                .map(l -> l.attr("content"))
                .flatMap(this::getMetaRedirectUrl)
                .map(link -> resolveRelativeUrl(baseUrl, link))
                .flatMap(this::createURI)
                .map(URI::normalize)
                .map(this::renormalize)
                .flatMap(this::createEdgeUrl);
    }

    // Matches the format of a meta http-equiv=refresh content tag, e.g. '10; url=http://example.com/'
    private static Pattern metaRedirectPattern = Pattern.compile("^\\d+\\s*;\\s*url=(\\S+)\\s*$");
    /** Parse the URL from a meta refresh tag, returning only the URL part and
     * discarding the rest.  Returns Optional.empty() on parse error. */
    private Optional<String> getMetaRedirectUrl(String content) {
        var matcher = metaRedirectPattern.matcher(content);

        if (!matcher.find())
            return Optional.empty();
        return Optional.ofNullable(matcher.group(1));
    }

    @SneakyThrows
    private URI renormalize(URI uri) {
        if (uri.getPath() == null) {
            return renormalize(new URI(uri.getScheme(), uri.getHost(), "/", uri.getQuery(), uri.getFragment()));
        }
        if (uri.getPath().startsWith("/../")) {
            return renormalize(new URI(uri.getScheme(), uri.getHost(), uri.getPath().substring(3), uri.getQuery(), uri.getFragment()));
        }
        return uri;
    }

    private String getUrl(Element element) {
        var url = CharMatcher.noneOf(" \r\n\t").retainFrom(element.attr("href"));

        int anchorIndex = url.indexOf('#');
        if (anchorIndex > 0) {
            return url.substring(0, anchorIndex);
        }
        return url;
    }

    private static final Pattern spaceRegex = Pattern.compile(" ");
    private static final Pattern paramSeparatorPattern = Pattern.compile("\\?");

    @SneakyThrows
    private String resolveRelativeUrl(EdgeUrl baseUrl, String s) {

        // url looks like http://www.marginalia.nu/
        if (doesUrlStringHaveProtocol(s)) {
            return s;
        }
        else if (s.startsWith("//")) { // scheme-relative URL
            return baseUrl.proto + ":" + s;
        }

        String[] parts = paramSeparatorPattern.split(s, 2);
        String path = parts[0];
        String param;
        if (parts.length > 1) {
            param = QueryParams.queryParamsSanitizer(parts[0], parts[1]);
        }
        else {
            param = null;
        }

        // url looks like /my-page
        if (path.startsWith("/")) {
            return baseUrl.withPathAndParam(path, param).toString();
        }

        final String partFromNewLink = spaceRegex.matcher(path).replaceAll("%20");

        return baseUrl.withPathAndParam(relativeNavigation(baseUrl) + partFromNewLink, param).toString();
    }

    // for a relative url that looks like /foo or /foo/bar; return / or /foo
    private String relativeNavigation(EdgeUrl url) {

        var lastSlash = url.path.lastIndexOf("/");
        if (lastSlash < 0) {
            return "/";
        }
        return url.path.substring(0, lastSlash+1);
    }

    private boolean doesUrlStringHaveProtocol(String s) {
        int i = 0;
        for (; i < s.length(); i++) {
            if (!Character.isAlphabetic(s.charAt(i)))
                break;
        }
        if (i == 0 || i == s.length())
            return false;
        return ':' == s.charAt(i);
    }

    public boolean shouldIndexLink(Element link) {
        return isUrlRelevant(link.attr("href"))
                && isRelRelevant(link.attr("rel"));
    }

    public boolean isRelRelevant(String rel) {
        // this is null safe
        return !"noindex".equalsIgnoreCase(rel);
    }

    private boolean isUrlRelevant(String href) {
        if (null == href || "".equals(href)) {
            return false;
        }
        if (href.length() > 128) {
            return false;
        }
        href = href.toLowerCase();

        if (blockedSchemaList.stream().anyMatch(href::startsWith)) {
            return false;
        }
        if (hasBinarySuffix(href)) {
            return false;
        }

        return true;
    }

    public boolean hasBinarySuffix(String str) {
        return binarySuffixList.stream().anyMatch(str::endsWith);
    }

    public EdgeUrl getBaseLink(Document parsed, EdgeUrl documentUrl) {
        var baseTags = parsed.getElementsByTag("base");

        try {
            for (var tag : baseTags) {
                String href = tag.attr("href");
                if (!Strings.isNullOrEmpty(href)) {
                    return new EdgeUrl(resolveRelativeUrl(documentUrl, href));
                }
            }
        }
        catch (Exception ex) {
            logger.warn("Failed to parse <base href=...>, falling back to document url");
        }

        return documentUrl;
    }

}
