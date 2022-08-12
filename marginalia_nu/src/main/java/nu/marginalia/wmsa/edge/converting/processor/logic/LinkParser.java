package nu.marginalia.wmsa.edge.converting.processor.logic;

import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;
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
    private final List<String> blockPrefixList = List.of(
            "mailto:", "javascript:", "tel:", "itpc:", "#", "file:");
    private final List<String> blockSuffixList = List.of(
            ".pdf", ".mp3", ".wmv", ".avi", ".zip", ".7z",
            ".bin", ".exe", ".tar.gz", ".tar.bz2", ".xml", ".swf",
            ".wav", ".ogg", ".jpg", ".jpeg", ".png", ".gif", ".webp",
            ".webm", ".bmp", ".doc", ".docx", ".ppt", ".pptx", ".xls", ".xlsx",
            ".gz", ".asc", ".md5", ".asf", ".mov", ".sig", ".pub", ".iso");

    @Contract(pure=true)
    public Optional<EdgeUrl> parseLink(EdgeUrl relativeBaseUrl, Element l) {
        return Optional.of(l)
                .filter(this::shouldIndexLink)
                .map(this::getUrl)
                .map(link -> resolveUrl(relativeBaseUrl, link))
                .flatMap(this::createURI)
                .map(URI::normalize)
                .map(this::renormalize)
                .flatMap(this::createEdgeUrl);
    }

    @Contract(pure=true)
    public Optional<EdgeUrl> parseLinkPermissive(EdgeUrl relativeBaseUrl, Element l) {
        return Optional.of(l)
                .map(this::getUrl)
                .map(link -> resolveUrl(relativeBaseUrl, link))
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
                .map(link -> resolveUrl(baseUrl, link))
                .flatMap(this::createURI)
                .map(URI::normalize)
                .map(this::renormalize)
                .flatMap(this::createEdgeUrl);
    }

    @Contract(pure=true)
    public Optional<EdgeUrl> parseFrame(EdgeUrl baseUrl, Element frame) {
        return Optional.of(frame)
                .map(l -> l.attr("src"))
                .map(link -> resolveUrl(baseUrl, link))
                .flatMap(this::createURI)
                .map(URI::normalize)
                .map(this::renormalize)
                .flatMap(this::createEdgeUrl);
    }

    @SneakyThrows
    private URI renormalize(URI uri) {
        if (uri.getPath() == null) {
            return renormalize(new URI(uri.getScheme(), uri.getHost(), "/", uri.getFragment()));
        }
        if (uri.getPath().startsWith("/../")) {
            return renormalize(new URI(uri.getScheme(), uri.getHost(), uri.getPath().substring(3), uri.getFragment()));
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

    @SneakyThrows
    private String resolveUrl(EdgeUrl baseUrl, String s) {

        // url looks like http://www.marginalia.nu/
        if (isAbsoluteDomain(s)) {
            return s;
        }

        String[] parts = s.split("\\?", 2);
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

    private boolean isAbsoluteDomain(String s) {
        return  s.matches("^[a-zA-Z]+:.*$");
    }

    public boolean shouldIndexLink(Element link) {
        return isUrlRelevant(link.attr("href"))
                && isRelRelevant(link.attr("rel"));
    }

    public boolean isRelRelevant(String rel) {
        // this is null safe
        return !"noindex".equalsIgnoreCase(rel);
    }

    public boolean hasBinarySuffix(String href) {
        return blockSuffixList.stream().anyMatch(href::endsWith);
    }

    private boolean isUrlRelevant(String href) {
        if (null == href || "".equals(href)) {
            return false;
        }
        if (blockPrefixList.stream().anyMatch(href::startsWith)) {
            return false;
        }
        if (hasBinarySuffix(href)) {
            return false;
        }
        if (href.length() > 128) {
            return false;
        }
        return true;
    }

    @Nullable
    public EdgeUrl getBaseLink(Document parsed, EdgeUrl documentUrl) {
        var baseTags = parsed.getElementsByTag("base");

        try {
            for (var tag : baseTags) {
                String href = tag.attr("href");
                if (!Strings.isNullOrEmpty(href)) {
                    return new EdgeUrl(resolveUrl(documentUrl, href));
                }
            }
        }
        catch (Exception ex) {
            logger.warn("Failed to parse <base href=...>, falling back to document url");
        }

        return documentUrl;
    }

}
