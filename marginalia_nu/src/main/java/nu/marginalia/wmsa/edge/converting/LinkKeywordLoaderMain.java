package nu.marginalia.wmsa.edge.converting;

import nu.marginalia.wmsa.configuration.module.DatabaseModule;
import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.edge.index.client.EdgeIndexClient;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.model.EdgeId;
import nu.marginalia.wmsa.edge.model.crawl.EdgePageWordSet;
import nu.marginalia.wmsa.edge.model.crawl.EdgePageWords;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Consumer;

public class LinkKeywordLoaderMain {

    public static void main(String... args) {

        Map<String, Long> urlToId = getUrls();
        try (EdgeIndexClient indexClient = new EdgeIndexClient();
             var lines = Files.lines(Path.of(args[0]))
        ) {
            lines
                    .map(UrlKeyword::parseLine)
                    .filter(Objects::nonNull)
                    .forEach(new Uploader(urlToId, indexClient));

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private record UrlKeyword(String url, String keyword) {
        public static UrlKeyword parseLine(String line) {
            String[] parts = line.split("\t");
            if (parts.length == 2) {
                return new UrlKeyword(parts[0], parts[1]);
            }
            return null;
        }
    }

    private static class Uploader implements Consumer<UrlKeyword> {
        private Map<String, Long> urlToId;
        private final EdgeIndexClient indexClient;

        private Uploader(Map<String, Long> urlToId,
                         EdgeIndexClient indexClient) {
            this.urlToId = urlToId;
            this.indexClient = indexClient;
        }

        String lastLine = null;
        Set<String> keywords = new HashSet<>(100);

        @Override
        public void accept(UrlKeyword urlKeyword) {
            if (urlKeyword == null) return;

            if (lastLine == null) {
                lastLine = urlKeyword.url;
                keywords.add(urlKeyword.keyword);
            }
            else if (urlKeyword.url.equals(lastLine)) {
                keywords.add(urlKeyword.keyword);
            }
            else {
                Long id = urlToId.get(lastLine);

                if (id != null) {
                    int urlId = (int)(id & 0xFFFF_FFFFL);
                    int domainId = (int)(id >>> 32L);

//                    System.out.println(lastLine + " -/- " + domainId + ":" + urlId + " : " + keywords);

                    indexClient.putWords(Context.internal(), new EdgeId<>(domainId), new EdgeId<>(urlId), -5, new EdgePageWordSet(
                            new EdgePageWords(IndexBlock.Link, new HashSet<>(keywords))), 0
                    ).blockingSubscribe();
                }

                lastLine = urlKeyword.url;
                keywords.clear();
                keywords.add(urlKeyword.keyword);
            }
        }
    }

    private static Map<String, Long>  getUrls() {

        Map<String, Long> urls = new HashMap<>(100_000);

        try (var ds = new DatabaseModule().provideConnection();
             var conn = ds.getConnection();
             var stmt = conn.createStatement())
        {
            var rsp = stmt.executeQuery("SELECT URL, ID, DOMAIN_ID FROM EC_URL_VIEW WHERE TITLE IS NOT NULL");

            while (rsp.next()) {
                long val = rsp.getInt(3);
                val = (val << 32L) | rsp.getInt(2);

                urls.put(rsp.getString(1), val);
            }

        }
        catch (SQLException ex) {
            throw new RuntimeException(ex);
        }

        return urls;
    }
}
