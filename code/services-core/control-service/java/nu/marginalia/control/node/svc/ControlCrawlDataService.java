package nu.marginalia.control.node.svc;

import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.executor.client.ExecutorClient;
import nu.marginalia.nodecfg.NodeConfigurationService;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorageId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Stream;

/** Service for inspecting crawl data within the control service.
 *
 * Uses remote calls to the executor service to fetch information about the crawl data.
 * Both directly, when inspecting the crawler log, and indirectly via duckdb when
 * inspecting the parquet files.  The duckdb calls rely on range queries to fetch
 * only the relevant data from the files, so that the UI remains responsive even when
 * dealing with large (100MB+ files).
 */
@Singleton
public class ControlCrawlDataService {
    private final ExecutorClient executorClient;
    private final FileStorageService fileStorageService;
    private final NodeConfigurationService nodeConfigurationService;

    private static final Logger logger = LoggerFactory.getLogger(ControlCrawlDataService.class);

    @Inject
    public ControlCrawlDataService(ExecutorClient executorClient,
                                   FileStorageService fileStorageService, NodeConfigurationService nodeConfigurationService)
    {
        this.executorClient = executorClient;
        this.fileStorageService = fileStorageService;
        this.nodeConfigurationService = nodeConfigurationService;
    }



    public Object crawlParquetInfo(Request request, Response response) throws SQLException {
        int nodeId = Integer.parseInt(request.params("id"));
        var fsid = FileStorageId.parse(request.queryParams("fid"));

        String path = request.queryParams("path");

        int after = Integer.parseInt(request.queryParamOrDefault("page", "0"));
        String urlGlob = request.queryParamOrDefault("urlGlob", "");
        String selectedContentType = request.queryParamOrDefault("contentType", "ALL");
        String selectedHttpStatus = request.queryParamOrDefault("httpStatus", "ALL");

        var url = executorClient.remoteFileURL(fileStorageService.getStorage(fsid), path).toString();

        List<SummaryStatusCode> byStatusCode = new ArrayList<>();
        List<SummaryContentType> byContentType = new ArrayList<>();

        List<CrawlDataRecordSummary> records = new ArrayList<>();

        String domain;
        try (var conn = DriverManager.getConnection("jdbc:duckdb:");
             var stmt = conn.createStatement()) {
            ResultSet rs;

            rs = stmt.executeQuery(DUCKDB."SELECT domain FROM \{url} LIMIT 1");
            domain = rs.next() ? rs.getString(1) : "NO DOMAIN";

            rs = stmt.executeQuery(DUCKDB."""
                                       SELECT httpStatus, COUNT(*) as cnt FROM \{url}
                                       GROUP BY httpStatus
                                       ORDER BY httpStatus
                                       """);
            while (rs.next()) {
                byStatusCode.add(new SummaryStatusCode(rs.getInt(1), rs.getInt(2), selectedHttpStatus.equals(rs.getString(1))));
            }

            rs = stmt.executeQuery(DUCKDB."""
                                        SELECT contentType, COUNT(*) as cnt
                                        FROM \{url}
                                        GROUP BY contentType
                                        ORDER BY contentType
                                        """);
            while (rs.next()) {
                byContentType.add(new SummaryContentType(rs.getString(1), rs.getInt(2), selectedContentType.equals(rs.getString(1))));
            }


            var query = DUCKDB."SELECT url, contentType, httpStatus, body != '', etagHeader, lastModifiedHeader FROM \{url} WHERE 1=1";
            if (!urlGlob.isBlank())
                query += DUCKDB." AND url LIKE \{urlGlob.replace('*', '%')}";
            if (!selectedContentType.equals("ALL"))
                query += DUCKDB." AND contentType = \{selectedContentType}";
            if (!selectedHttpStatus.equals("ALL"))
                query += DUCKDB." AND httpStatus = \{selectedHttpStatus}";
            query += DUCKDB." LIMIT 10 OFFSET \{after}";

            rs = stmt.executeQuery(query);
            while (rs.next()) {
                records.add(new CrawlDataRecordSummary(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getBoolean(4), rs.getString(5), rs.getString(6)));
            }
        }

        Map<String, Object> ret = new HashMap<>();

        ret.put("tab", Map.of("storage", true));
        ret.put("view", Map.of("crawl", true));
        ret.put("selectedContentType", Map.of(selectedContentType, true));
        ret.put("selectedHttpStatus", Map.of(selectedHttpStatus, true));
        ret.put("urlGlob", urlGlob);

        ret.put("pagination", new Pagination(after + 10, after - 10));

        ret.put("node", nodeConfigurationService.get(nodeId));
        ret.put("storage", fileStorageService.getStorage(fsid));
        ret.put("path", path);
        ret.put("domain", domain);
        ret.put("byStatusCode", byStatusCode);
        ret.put("byContentType", byContentType);
        ret.put("records", records);

        return ret;
    }

    public ControlCrawlDataService.CrawlDataFileList getCrawlDataFiles(FileStorageId fsid,
                                                                        @Nullable String filterDomain,
                                                                        @Nullable String afterDomain) {
        List<ControlCrawlDataService.CrawlDataFile> crawlDataFiles = new ArrayList<>();

        try (var br = new BufferedReader(new InputStreamReader((executorClient.remoteFileURL(fileStorageService.getStorage(fsid), "crawler.log").openStream())))) {
            Stream<CrawlDataFile> str = br.lines()
                    .filter(s -> !s.isBlank())
                    .filter(s -> !s.startsWith("#"))
                    .map(s -> {
                        String[] parts = s.split("\\s+");
                        return new ControlCrawlDataService.CrawlDataFile(parts[0], parts[2], Integer.parseInt(parts[3]));
                    });

            if (!Strings.isNullOrEmpty(afterDomain)) {
                str = str.dropWhile(s -> !s.domain().equals(afterDomain)).skip(1);
            }

            if (!Strings.isNullOrEmpty(filterDomain)) {
                str = str.filter(s -> s.domain().toLowerCase().contains(filterDomain.toLowerCase()));
            }

            str.limit(10).forEach(crawlDataFiles::add);
        }
        catch (Exception ex) {
            logger.warn("Failed to fetch crawler.log", ex);
        }

        return new ControlCrawlDataService.CrawlDataFileList(
                crawlDataFiles,
                filterDomain,
                afterDomain);
    }

    public record SummaryContentType(String contentType, int count, boolean filtered) {}

    public record SummaryStatusCode(int statusCode, int count, boolean filtered) {}
    public record Pagination(int next, int prev) {
        public boolean isPrevPage() {
            return prev >= 0;
        }
    }

    public record CrawlDataRecordSummary(String url, String contentType, int httpStatus, boolean hasBody, String etag, String lastModified) {
        public boolean isGood() { return httpStatus >= 200 && httpStatus < 300; }
        public boolean isRedirect() { return httpStatus >= 300 && httpStatus < 400; }
        public boolean isClientError() { return httpStatus >= 400 && httpStatus < 500; }
        public boolean isServerError() { return httpStatus >= 500; }
        public boolean isUnknownStatus() { return httpStatus < 200; }
    }

    public record CrawlDataFile(String domain, String path, int count) {}

    public record CrawlDataFileList(List<CrawlDataFile> files,
                                           @Nullable String filter,
                                           @Nullable String after)
    {

        // Used by the template to determine if there are more files to show,
        // looks unused in the IDE but it's not
        public String nextAfter() {
            if (files.isEmpty())
                return "";
            if (files.size() < 10)
                return "";

            return files.getLast().domain();
        }
    }

    // DuckDB template processor that deals with quoting and escaping values
    // in the SQL query; this offers a very basic protection against accidental SQL injection
    static StringTemplate.Processor<String, IllegalArgumentException> DUCKDB = st -> {
        StringBuilder sb = new StringBuilder();
        Iterator<String> fragmentsIter = st.fragments().iterator();

        for (Object value : st.values()) {
            sb.append(fragmentsIter.next());

            if (value instanceof Number) { // don't quote numbers
                sb.append(value);
            } else {
                String valueStr = value.toString().replace("'", "''");
                sb.append("'").append(valueStr).append("'");
            }
        }

        sb.append(fragmentsIter.next());

        return sb.toString();
    };

}
