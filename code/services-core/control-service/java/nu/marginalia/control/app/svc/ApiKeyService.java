package nu.marginalia.control.app.svc;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.control.ControlRendererFactory;
import nu.marginalia.control.Redirects;
import nu.marginalia.control.app.model.ApiKeyModel;
import org.eclipse.jetty.util.StringUtil;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ApiKeyService {

    private final HikariDataSource dataSource;
    private final ControlRendererFactory rendererFactory;

    @Inject
    public ApiKeyService(HikariDataSource dataSource,
                         ControlRendererFactory rendererFactory
                         ) {
        this.dataSource = dataSource;
        this.rendererFactory = rendererFactory;
    }

    public void register() throws IOException {

        var apiKeysRenderer = rendererFactory.renderer("control/app/api-keys");

        Spark.get("/api-keys", this::apiKeysModel, apiKeysRenderer::render);
        Spark.post("/api-keys", this::createApiKey, Redirects.redirectToApiKeys);
        Spark.delete("/api-keys/:key", this::deleteApiKey, Redirects.redirectToApiKeys);
        // HTML forms don't support the DELETE verb :-(
        Spark.post("/api-keys/:key/delete", this::deleteApiKey, Redirects.redirectToApiKeys);

    }

    private Object createApiKey(Request request, Response response) {
        String license = request.queryParams("license");
        String name = request.queryParams("name");
        String email = request.queryParams("email");
        int rate = Integer.parseInt(request.queryParams("rate"));

        if (StringUtil.isBlank(license) ||
                StringUtil.isBlank(name) ||
                StringUtil.isBlank(email) ||
                rate <= 0)
        {
            response.status(400);
            return "";
        }

        addApiKey(license, name, email, rate);

        return "";
    }

    private Object deleteApiKey(Request request, Response response) {
        String licenseKey = request.params("key");
        deleteApiKey(licenseKey);
        return "";
    }

    private Object apiKeysModel(Request request, Response response) {
        return Map.of("apikeys", getApiKeys());
    }



    public List<ApiKeyModel> getApiKeys() {
        try (var conn = dataSource.getConnection()) {
            try (var stmt = conn.prepareStatement("""
                    SELECT LICENSE_KEY, LICENSE, NAME, EMAIL, RATE FROM EC_API_KEY
                    """)) {
                List<ApiKeyModel> ret = new ArrayList<>(100);
                var rs = stmt.executeQuery();
                while (rs.next()) {
                    ret.add(new ApiKeyModel(
                            rs.getString("LICENSE_KEY"),
                            rs.getString("LICENSE"),
                            rs.getString("NAME"),
                            rs.getString("EMAIL"),
                            rs.getInt("RATE")));
                }
                return ret;
            }
        }
        catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    public ApiKeyModel addApiKey(String license, String name, String email, int rate) {
        try (var conn = dataSource.getConnection()) {
            try (var insertStmt = conn.prepareStatement("""
                    INSERT INTO EC_API_KEY (LICENSE_KEY, LICENSE, NAME, EMAIL, RATE) SELECT SHA(?), ?, ?, ?, ?
                    """);
                 // we could do SELECT SHA(?) here I guess if performance was a factor, but it's not
                 var queryStmt = conn.prepareStatement("SELECT LICENSE_KEY FROM EC_API_KEY WHERE LICENSE_KEY = SHA(?)")
            ) {
                final String seedString = UUID.randomUUID() + "-" + name + "-" + email;

                insertStmt.setString(1, seedString);
                insertStmt.setString(2, license);
                insertStmt.setString(3, name);
                insertStmt.setString(4, email);
                insertStmt.setInt(5, rate);
                insertStmt.executeUpdate();

                queryStmt.setString(1, seedString);
                var rs = queryStmt.executeQuery();
                if (rs.next()) {
                    return new ApiKeyModel(
                            rs.getString("LICENSE_KEY"),
                            license,
                            name,
                            email,
                            rate);
                }

                throw new RuntimeException("Failed to insert key");
            }
        }
        catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void deleteApiKey(String key) {
        try (var conn = dataSource.getConnection()) {
            try (var stmt = conn.prepareStatement("""
                    DELETE FROM EC_API_KEY WHERE LICENSE_KEY = ?
                    """)) {
                stmt.setString(1, key);
                stmt.executeUpdate();
            }
        }
        catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }
}
