package nu.marginalia.converting.sideload.encyclopedia;

import com.github.luben.zstd.ZstdInputStream;
import com.google.gson.Gson;
import lombok.SneakyThrows;
import nu.marginalia.converting.model.DisqualifiedException;
import nu.marginalia.converting.model.ProcessedDocument;
import nu.marginalia.converting.model.ProcessedDomain;
import nu.marginalia.converting.sideload.SideloadSource;
import nu.marginalia.converting.sideload.SideloaderProcessing;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawl.DomainIndexingState;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.*;
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
    private final EdgeUrl baseUrl;
    private final Gson gson;
    private final SideloaderProcessing sideloaderProcessing;

    public EncyclopediaMarginaliaNuSideloader(Path pathToDbFile,
                                              String baseUrl,
                                              Gson gson,
                                              SideloaderProcessing sideloaderProcessing) throws SQLException {
        this.baseUrl = EdgeUrl.parse(baseUrl).orElseThrow(AssertionError::new);
        this.gson = gson;
        this.sideloaderProcessing = sideloaderProcessing;
        String sqliteDbString = "jdbc:sqlite:" + pathToDbFile.toString();

        connection = DriverManager.getConnection(sqliteDbString);

    }

    @Override
    public ProcessedDomain getDomain() {
        var ret = new ProcessedDomain();

        ret.domain = baseUrl.getDomain();
        ret.ip = "0.0.0.0";
        ret.state = DomainIndexingState.ACTIVE;

        return ret;
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
                    String url = URLEncoder.encode(rs.getString("url"), StandardCharsets.UTF_8);

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

    ProcessedDocument processJust(String url) throws SQLException, IOException, URISyntaxException, DisqualifiedException {
        var stmt = connection.prepareStatement("""
                SELECT url,title,html FROM articles
                WHERE url=?
                """);
        stmt.setFetchSize(100);
        stmt.setString(1, url);

        var rs = stmt.executeQuery();
        if (rs.next()) {
            var articleParts = fromCompressedJson(rs.getBytes("html"), ArticleParts.class);
            String title = rs.getString("title");

            return convertDocument(articleParts.parts, title, URLEncoder.encode(rs.getString("url"), StandardCharsets.UTF_8));
        }
        return null;
    }

    private ProcessedDocument convertDocument(List<String> parts, String title, String url) throws URISyntaxException, DisqualifiedException {
        String fullUrl = baseUrl.toString() + url;

        StringBuilder fullHtml = new StringBuilder();
        fullHtml.append("<!DOCTYPE html><html><head><title>").append(title).append("</title></head><body>");
        for (String part : parts) {
            fullHtml.append("<p>");
            fullHtml.append(part);
            fullHtml.append("</p>");
        }
        fullHtml.append("</body></html>");

        return sideloaderProcessing
                .processDocument(fullUrl,
                        fullHtml.toString(),
                        List.of("encyclopedia", "wiki"),
                        10_000_000);
    }

    private <T> T fromCompressedJson(byte[] stream, Class<T> type) throws IOException {
        return gson.fromJson(new InputStreamReader(new ZstdInputStream(new ByteArrayInputStream(stream))), type);
    }

    private record ArticleParts(List<String> parts) {}

    @Override
    public void close() throws Exception {
        connection.close();
    }
}
