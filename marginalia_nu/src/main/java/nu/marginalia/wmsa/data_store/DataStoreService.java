package nu.marginalia.wmsa.data_store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.zaxxer.hikari.HikariDataSource;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.configuration.server.Initialization;
import nu.marginalia.wmsa.configuration.server.MetricsServer;
import nu.marginalia.wmsa.configuration.server.Service;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import static spark.Spark.*;

public class DataStoreService extends Service {
    private final HikariDataSource dataSource;
    private final EdgeDataStoreService edgeService;
    private Logger logger = LoggerFactory.getLogger(getClass());
    private final Gson gson = new GsonBuilder().create();

    @Inject
    public DataStoreService(
            @Named("service-host") String ip,
            @Named("service-port") Integer port,
            FileRepository fileRepo,
            HikariDataSource dataSource,
            EdgeDataStoreService edgeService,
            Initialization init,
            MetricsServer metricsServer
            ) {
        super(ip, port, init, metricsServer);

        this.dataSource = dataSource;
        this.edgeService = edgeService;

        Spark.get("data/:domain/:model/:resource", this::getResource);
        Spark.get("data/:domain/:model", this::getResourceIdsForModel, this::convertToJson);
        post("data/:domain/:model/:resource", this::storeResource);

        post("release", fileRepo::uploadFile);
        Spark.get("release", fileRepo::downloadFile);
        Spark.get("release/upload", fileRepo::uploadForm);
        Spark.get("release/version", fileRepo::version);

        Spark.path("edge", () -> {
            post("/domain-alias/*/*", edgeService::putDomainAlias, this::convertToJson);
            post("/link", edgeService::putLink, this::convertToJson);

            post("/url", edgeService::putUrl, this::convertToJson);
            post("/url-visited", edgeService::putUrlVisited, this::convertToJson);
            get("/url/:id", edgeService::getUrlName, this::convertToJson);
            get("/domain-id/*", edgeService::getDomainId, this::convertToJson);
            get("/domain/:id", edgeService::getDomainName, this::convertToJson);
            get("/meta/:site", edgeService::domainInfo, this::convertToJson);

        });


    }

    private String convertToJson(Object o) {
        return gson.toJson(o);
    }

    @SneakyThrows
    private Object getResourceIdsForModel(Request request, Response response) {
        try (var connection = dataSource.getConnection()) {

            String model = request.params("model");
            String domain = request.params("domain");

            String query = String.format("SELECT ID FROM JSON_DATA WHERE MODEL='%s' AND DOM='%s'",
                    model, domain);


            List<String> ids = new ArrayList<>();
            try (var stmt = connection.createStatement()) {
                var rs = stmt.executeQuery(query);
                while (rs.next()) {
                    ids.add(rs.getString(1));
                }
            }

            return ids;
        }
    }


    @SneakyThrows
    private Object getResource(Request request, Response response) {
        try (var connection = dataSource.getConnection()) {

            String resource = request.params("resource");
            String model = request.params("model");
            String domain = request.params("domain");

            String query = String.format("SELECT DATA FROM JSON_DATA WHERE ID='%s' AND MODEL='%s' AND DOM='%s'",
                    resource, model, domain);

            try (var stmt = connection.createStatement()) {
                var rs = stmt.executeQuery(query);
                if (!rs.next()) {
                    halt(404);
                }

                rs.getAsciiStream(1).transferTo(response.raw().getOutputStream());

                if (rs.next()) {
                    logger.warn("Duplicate data for {}/{}/{}", domain, model, resource);
                }
            }

            return "";
        }
    }

    @SneakyThrows
    private Object storeResource(Request request, Response response) {
        try (var connection = dataSource.getConnection()) {

            String resource = request.params("resource");
            String model = request.params("model");
            String domain = request.params("domain");

            try (var stmt = connection.prepareStatement("INSERT INTO JSON_DATA(dom, model, id, data) VALUES (?,?,?,?)")) {
                stmt.setString(1, domain);
                stmt.setString(2, model);
                stmt.setString(3, resource);
                stmt.setCharacterStream(4, new InputStreamReader(request.raw().getInputStream()));

                stmt.executeUpdate();

                if (stmt.getUpdateCount() != 1) {
                    logger.warn("Query failed");
                    halt(500);
                }
            }
            halt(HttpStatus.ACCEPTED_202);
            return null;
        }
    }
}
