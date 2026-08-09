package nu.marginalia.converting.sideload.encyclopedia;

import com.github.luben.zstd.ZstdInputStream;
import com.google.common.base.Charsets;
import com.google.gson.Gson;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import nu.marginalia.atags.AnchorTextKeywords;
import nu.marginalia.atags.model.DomainLinks;
import nu.marginalia.atags.source.AnchorTagsSourceFactory;
import nu.marginalia.converting.model.DisqualifiedException;
import nu.marginalia.converting.model.GeneratorType;
import nu.marginalia.converting.model.ProcessedDocument;
import nu.marginalia.converting.model.ProcessedDomain;
import nu.marginalia.converting.processor.DocumentClass;
import nu.marginalia.converting.sideload.SideloadSource;
import nu.marginalia.converting.sideload.SideloaderProcessing;
import nu.marginalia.hash.MurmurHash3_128;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawl.DomainIndexingState;
import nu.marginalia.model.crawl.PubDate;
import nu.marginalia.util.ProcessingIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.List;

/** This is an experimental sideloader for encyclopedia.marginalia.nu's database;
 * (which serves as a way of loading wikipedia's zim files without binding to GPL2'd code)
 * <p>
 * See https://github.com/MarginaliaSearch/encyclopedia.marginalia.nu for extracting the data
 */
public class EncyclopediaMarginaliaNuSideloader implements SideloadSource, AutoCloseable {

    private final Connection connection;
    private final EdgeUrl baseUrl;
    private final Gson gson;
    private final AnchorTextKeywords anchorTextKeywords;
    private final SideloaderProcessing sideloaderProcessing;
    private final AnchorTagsSourceFactory anchorTagsSourceFactory;
    private static final MurmurHash3_128 hash = new MurmurHash3_128();
    private static final Logger logger = LoggerFactory.getLogger(EncyclopediaMarginaliaNuSideloader.class);

    public EncyclopediaMarginaliaNuSideloader(Path pathToDbFile,
                                              String baseUrl,
                                              Gson gson,
                                              AnchorTagsSourceFactory anchorTagsSourceFactory,
                                              AnchorTextKeywords anchorTextKeywords,
                                              SideloaderProcessing sideloaderProcessing) throws SQLException {
        this.baseUrl = EdgeUrl.parse(baseUrl).orElseThrow(AssertionError::new);
        this.gson = gson;
        this.anchorTextKeywords = anchorTextKeywords;
        this.sideloaderProcessing = sideloaderProcessing;
        String sqliteDbString = "jdbc:sqlite:" + pathToDbFile.toString();

        connection = DriverManager.getConnection(sqliteDbString);
        this.anchorTagsSourceFactory = anchorTagsSourceFactory;

    }

    @Override
    public ProcessedDomain getDomain() {
        var ret = new ProcessedDomain();

        ret.domain = baseUrl.getDomain();
        ret.ip = "0.0.0.0";
        ret.state = DomainIndexingState.ACTIVE;
        ret.sizeloadSizeAdvice = 5_000_000;
        return ret;
    }

    @Override
    public Iterator<ProcessedDocument> getDocumentsStream() {
        // This leaks a thread pool, but it doesn't matter since this is a one-off process
        return ProcessingIterator.factory(24, 16).create((taskConsumer) -> {
            DomainLinks domainLinks = getDomainLinks();
            Long2IntOpenHashMap incomingLinkCounts = countIntraWikiLinks();

            var stmt = connection.prepareStatement("""
                    SELECT url,title,html FROM articles
                    """);
            stmt.setFetchSize(100);

            var rs = stmt.executeQuery();

            while (rs.next()) {
                var articleParts = fromCompressedJson(rs.getBytes("html"), ArticleParts.class);
                String title = rs.getString("title");
                String url = rs.getString("url");

                int topology = incomingLinkCounts.get(hash.hashUtf8(url));

                taskConsumer.accept(() -> convertDocument(articleParts.parts, title, url, topology, domainLinks));
            }
        });
    }

    private DomainLinks getDomainLinks() {
        try (var source = anchorTagsSourceFactory.create(List.of(new EdgeDomain("en.wikipedia.org")))) {
            return source.getAnchorTags("en.wikipedia.org");
        }
        catch (Exception ex) {
            logger.error("Failed to create anchor tags source", ex);
            return new DomainLinks();
        }
    }

    /** Count each article's incoming links from within the wiki, mirroring the
     * topology signal crawled domains get from their internal link graph. */
    private Long2IntOpenHashMap countIntraWikiLinks() throws SQLException, IOException {
        Long2IntOpenHashMap counts = new Long2IntOpenHashMap(8_000_000);

        var stmt = connection.prepareStatement("""
                SELECT urls FROM articles
                """);
        stmt.setFetchSize(100);

        var rs = stmt.executeQuery();
        while (rs.next()) {
            var urls = fromCompressedJson(rs.getBytes("urls"), ArticleUrls.class);
            if (urls.links() == null)
                continue;

            for (var link : urls.links()) {
                if (link.url() == null)
                    continue;

                // Link targets are percent encoded while the url column holds the raw name
                String target;
                try {
                    target = URLDecoder.decode(link.url(), Charsets.UTF_8);
                }
                catch (IllegalArgumentException e) {
                    target = link.url();
                }

                counts.addTo(hash.hashUtf8(target), 1);
            }
        }

        logger.info("Counted intra-wiki links for {} articles", counts.size());

        return counts;
    }

    private ProcessedDocument convertDocument(List<String> parts, String title, String url, int topology, DomainLinks domainLinks) throws URISyntaxException, DisqualifiedException {
        String fullUrl = baseUrl.toString() + URLEncoder.encode(normalizeUtf8(url), Charsets.UTF_8);

        StringBuilder fullHtml = new StringBuilder();
        fullHtml
                .append("<!DOCTYPE html><html><head><title>")
                .append(title)
                .append("</title></head><body>")
                .append("<script src=\"https://www.example.com/dummy.js\" type=\"text/javascript\"></script>")
                .append("<div class=\"mw-content-text\">");

        for (String part : parts) {
            fullHtml.append("<p>");
            fullHtml.append(part);
            fullHtml.append("</p>");
        }
        fullHtml.append("</div></body></html>");

        var doc = sideloaderProcessing
                .processDocument(fullUrl,
                        fullHtml.toString(),
                        List.of("encyclopedia", "wiki"),
                        domainLinks,
                        GeneratorType.WIKI,
                        DocumentClass.SIDELOAD,
                        anchorTextKeywords.getAnchorTextKeywords(domainLinks, new EdgeUrl(fullUrl)),
                        LocalDate.now().getYear(),
                        PubDate.INVALID_DATE_SENTINEL,
                        10_000_000);

        // Overrides the topology set by SideloaderProcessing, which only sees the anchor tag data
        if (doc.details != null && doc.details.metadata != null) {
            int atagsCount = domainLinks.countForUrl(new EdgeUrl(fullUrl));
            doc.details.metadata = doc.details.metadata.withSizeAndTopology(10_000_000, topology + atagsCount);
        }

        return doc;
    }

    private String normalizeUtf8(String url) {
        // A rare number of articles have incorrectly normalized UTF-8 in their paths.
        // This is a stopgap to fix them, as the URLs break if you urlencode the UTF-8.

        return url
                .replace('\u2013', '-')
                ;
    }

    private <T> T fromCompressedJson(byte[] stream, Class<T> type) throws IOException {
        return gson.fromJson(new InputStreamReader(new ZstdInputStream(new ByteArrayInputStream(stream))), type);
    }

    private record ArticleParts(List<String> parts) {}

    private record ArticleUrls(List<ArticleLink> links) {}

    private record ArticleLink(String url, String text) {}

    @Override
    public void close() throws Exception {
        connection.close();
    }
}
