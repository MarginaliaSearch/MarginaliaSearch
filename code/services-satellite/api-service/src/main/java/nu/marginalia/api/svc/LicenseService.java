package nu.marginalia.api.svc;

import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.api.model.ApiLicense;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Spark;

import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class LicenseService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final HikariDataSource dataSource;
    private final ConcurrentHashMap<String, ApiLicense> licenseCache = new ConcurrentHashMap<>();

    @Inject
    public LicenseService(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    @NotNull
    public ApiLicense getLicense(Request request) {
        final String key = request.params("key");

        if (Strings.isNullOrEmpty(key)) {
            Spark.halt(400, "Bad key");
        }

        return licenseCache.computeIfAbsent(key, this::getFromDb);
    }

    private ApiLicense getFromDb(String key) {
        try (var conn = dataSource.getConnection();
            var stmt = conn.prepareStatement("SELECT LICENSE,NAME,RATE FROM EC_API_KEY WHERE LICENSE_KEY=?")) {

            stmt.setString(1, key);

            var rsp = stmt.executeQuery();

            if (rsp.next()) {
                return new ApiLicense(key, rsp.getString(1), rsp.getString(2), rsp.getInt(3));
            }

        }
        catch (Exception ex) {
            logger.error("Bad request", ex);
            Spark.halt(500);
        }

        Spark.halt(401, "Invalid license key");

        throw new IllegalStateException("This is unreachable");
    }
}
