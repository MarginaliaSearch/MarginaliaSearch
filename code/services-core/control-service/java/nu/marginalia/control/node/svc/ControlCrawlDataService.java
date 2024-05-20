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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
        var fsid = FileStorageId.parse(request.params("fid"));

        String path = request.queryParams("path");

        var url = executorClient.remoteFileURL(fileStorageService.getStorage(fsid), path).toString();

        List<SummaryStatusCode> byStatusCode = new ArrayList<>();
        List<SummaryContentType> byContentType = new ArrayList<>();

        List<CrawlDataRecordSummary> records = new ArrayList<>();

        String domain;
        try (var conn = DriverManager.getConnection("jdbc:duckdb:");
             var stmt = conn.createStatement())
        {
            ResultSet rs;

            rs = stmt.executeQuery(DUCKDB."SELECT domain FROM \{url} LIMIT 1");
            domain = rs.next() ? rs.getString(1) : "NO DOMAIN";

            rs = stmt.executeQuery(DUCKDB."""
                                       SELECT httpStatus, COUNT(*) as cnt FROM \{url}
                                       GROUP BY httpStatus
                                       ORDER BY httpStatus
                                       """);
            while (rs.next()) {
                byStatusCode.add(new SummaryStatusCode(rs.getInt(1), rs.getInt(2)));
            }

            rs = stmt.executeQuery(DUCKDB."""
                                        SELECT contentType, COUNT(*) as cnt
                                        FROM \{url}
                                        GROUP BY contentType
                                        ORDER BY contentType
                                        """);
            while (rs.next()) {
                byContentType.add(new SummaryContentType(rs.getString(1), rs.getInt(2)));
            }

            rs = stmt.executeQuery(DUCKDB."""
                                          SELECT url, contentType, httpStatus, body != '', etagHeader, lastModifiedHeader
                                          FROM \{url} LIMIT 10
                                          """);
            while (rs.next()) {
                records.add(new CrawlDataRecordSummary(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getBoolean(4), rs.getString(5), rs.getString(6)));
            }
        }

        return Map.of(
                "tab", Map.of("storage", true),
                "view", Map.of("crawl", true),
                "node", nodeConfigurationService.get(nodeId),
                "storage", fileStorageService.getStorage(fsid),
                "path", path,
                "domain", domain,
                "byStatusCode", byStatusCode,
                "byContentType", byContentType,
                "records", records)
                ;
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

    public record SummaryContentType(String contentType, int count) {}

    public record SummaryStatusCode(int statusCode, int count) {}

    public record CrawlDataRecordSummary(String url, String contentType, int httpStatus, boolean hasBody, String etag, String lastModified) {}

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
