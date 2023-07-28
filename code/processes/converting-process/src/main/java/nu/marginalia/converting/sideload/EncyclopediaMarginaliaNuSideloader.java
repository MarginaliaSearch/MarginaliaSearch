package nu.marginalia.converting.sideload;

import com.github.luben.zstd.ZstdInputStream;
import com.google.gson.Gson;
import lombok.SneakyThrows;
import nu.marginalia.converting.model.DisqualifiedException;
import nu.marginalia.converting.model.ProcessedDocument;
import nu.marginalia.converting.model.ProcessedDomain;
import nu.marginalia.converting.processor.plugin.HtmlDocumentProcessorPlugin;
import nu.marginalia.crawling.model.CrawledDocument;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawl.DomainIndexingState;
import nu.marginalia.model.crawl.UrlIndexingState;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/** This is an experimental sideloader for encyclopedia.marginalia.nu's database;
 * (which serves as a way of loading wikipedia's zim files without binding to GPL2'd code)
 *
 * See https://github.com/MarginaliaSearch/encyclopedia.marginalia.nu for extracting the data
 */
public class EncyclopediaMarginaliaNuSideloader implements SideloadSource, AutoCloseable {

    private final Connection connection;
    private final Gson gson;
    private final HtmlDocumentProcessorPlugin htmlProcessorPlugin;

    public EncyclopediaMarginaliaNuSideloader(Path pathToDbFile,
                                              Gson gson,
                                              HtmlDocumentProcessorPlugin htmlProcessorPlugin) throws SQLException {
        this.gson = gson;
        this.htmlProcessorPlugin = htmlProcessorPlugin;
        String sqliteDbString = "jdbc:sqlite:" + pathToDbFile.toString();

        connection = DriverManager.getConnection(sqliteDbString);

    }

    @Override
    public ProcessedDomain getDomain() {
        var ret = new ProcessedDomain();

        ret.domain = new EdgeDomain("encyclopedia.marginalia.nu");
        ret.id = "encyclopedia.marginalia.nu";
        ret.ip = "127.0.0.1";
        ret.state = DomainIndexingState.ACTIVE;

        return ret;
    }

    @Override
    @SneakyThrows
    public Iterator<EdgeUrl> getUrlsIterator() {
        EdgeUrl base = new EdgeUrl("https://encyclopedia.marginalia.nu/");

        return new SqlQueryIterator<>(connection.prepareStatement("""
                SELECT url, html FROM articles
                """))
        {
            @Override
            public EdgeUrl convert(ResultSet rs) throws Exception {
                var path = URLEncoder.encode(rs.getString("url"), StandardCharsets.UTF_8);

                return base.withPathAndParam("/article/"+path, null);
            }
        };
    }


    @SneakyThrows
    @Override
    public Iterator<ProcessedDocument> getDocumentsStream() {
        LinkedBlockingQueue<ProcessedDocument> docs = new LinkedBlockingQueue<>(32);
        AtomicBoolean isFinished = new AtomicBoolean(false);

        ExecutorService executorService = Executors.newFixedThreadPool(16);
        Semaphore sem = new Semaphore(16);

        executorService.submit(() -> {
            try {
                var stmt = connection.prepareStatement("""
                        SELECT url,title,html FROM articles
                        """);
                stmt.setFetchSize(100);

                var rs = stmt.executeQuery();
                while (rs.next()) {
                    var articleParts = fromCompressedJson(rs.getBytes("html"), ArticleParts.class);
                    String title = rs.getString("title");
                    String url = rs.getString("url");

                    sem.acquire();

                    executorService.submit(() -> {
                        try {
                            docs.add(convertDocument(articleParts.parts, title, url));
                        } catch (URISyntaxException | DisqualifiedException e) {
                            e.printStackTrace();
                        } finally {
                            sem.release();
                        }
                    });
                }

                stmt.close();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            finally {
                isFinished.set(true);
            }
        });

        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return !isFinished.get() || !docs.isEmpty() || sem.availablePermits() < 16;
            }

            @SneakyThrows
            @Override
            public ProcessedDocument next() {
                return docs.take();
            }
        };
    }

    private ProcessedDocument convertDocument(List<String> parts, String title, String url) throws URISyntaxException, DisqualifiedException {
        String fullUrl = "https://encyclopedia.marginalia.nu/article/"+url;

        StringBuilder fullHtml = new StringBuilder();
        fullHtml.append("<!DOCTYPE html><html><head><title>").append(title).append("</title></head><body>");
        for (String part : parts) {
            fullHtml.append("<p>");
            fullHtml.append(part);
            fullHtml.append("</p>");
        }
        fullHtml.append("</body></html>");

        var crawledDoc = new CrawledDocument(
                "encyclopedia.marginalia.nu",
                fullUrl,
                "text/html",
                LocalDateTime.now().toString(),
                200,
                "OK",
                "NP",
                "",
                fullHtml.toString(),
                Integer.toHexString(fullHtml.hashCode()),
                fullUrl,
                "",
                "SIDELOAD"
        );

        var ret = new ProcessedDocument();
        try {
            var details = htmlProcessorPlugin.createDetails(crawledDoc);

            ret.words = details.words();
            ret.details = details.details();
            ret.url = new EdgeUrl(fullUrl);
            ret.state = UrlIndexingState.OK;
            ret.stateReason = "SIDELOAD";
        }
        catch (Exception e) {
            ret.url = new EdgeUrl(fullUrl);
            ret.state = UrlIndexingState.DISQUALIFIED;
            ret.stateReason = "SIDELOAD";
        }

        return ret;

    }

    private <T> T fromCompressedJson(byte[] stream, Class<T> type) throws IOException {
        return gson.fromJson(new InputStreamReader(new ZstdInputStream(new ByteArrayInputStream(stream))), type);
    }

    private record ArticleParts(List<String> parts) {}

    @Override
    public String getId() {
        return "encyclopedia.marginalia.nu";
    }

    @Override
    public void close() throws Exception {
        connection.close();
    }

    private abstract static class SqlQueryIterator<T> implements Iterator<T> {
        PreparedStatement stmt;
        ResultSet rs;
        T next = null;

        public SqlQueryIterator(PreparedStatement stmt) throws SQLException {
            this.stmt = stmt;
            stmt.setFetchSize(1000);
            rs = stmt.executeQuery();
        }

        @SneakyThrows
        @Override
        public boolean hasNext() {
            if (next != null) {
                return true;
            }
            if (!rs.next()) {
                stmt.close();
                return false;
            }

            next = convert(rs);

            return true;
        }

        public abstract T convert(ResultSet rs) throws Exception;

        @Override
        public T next () {
            if (!hasNext())
                throw new IllegalStateException("No next element");
            var ret = next;
            next = null;
            return ret;
        }
    }
}
