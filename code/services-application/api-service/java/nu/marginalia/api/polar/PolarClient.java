package nu.marginalia.api.polar;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import jakarta.inject.Named;
import nu.marginalia.model.gson.GsonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;

@Singleton
public class PolarClient {

    private final HttpClient client;
    private final String baseUri;

    private final String accessToken;
    private final String orgId;

    private final Gson gson = GsonFactory.get();
    private static final Logger logger = LoggerFactory.getLogger(PolarClient.class);

    private final boolean isAvilable;

    private ConcurrentHashMap<String, PolarLicenseKey> licenses = new ConcurrentHashMap<>();

    private final Executor virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledFuture<?> revalidationJob;

    @Inject
    public PolarClient(
            @Named("polar-base-uri") String baseUri,
            @Named("polar-access-token") String accessToken,
            @Named("polar-org-id") String orgId
    )
    {
        this.baseUri = baseUri;
        this.accessToken = accessToken;
        this.orgId = orgId;

        if (accessToken == null || baseUri == null || orgId == null) {
            logger.error("Missing credentials for polar.sh integration, disabling");

            this.client = null;
            this.isAvilable = false;
            this.revalidationJob = null;
        }
        else {
            logger.info("Polar integration enabled!");

            this.client = java.net.http.HttpClient.newBuilder()
                    .executor(virtualExecutor)
                    .build();
            this.isAvilable = true;

            revalidationJob = Executors.newScheduledThreadPool(1).scheduleWithFixedDelay(
                    this::revalidateKeys,
                    1,
                    1,
                    TimeUnit.HOURS
            );
        }

    }

    private void revalidateKeys() {

        logger.info("Revalidating Polar keys");

        List<String> keys = new ArrayList<>(licenses.keySet());

        for (var key : keys) {
            fetchLicenseKey(key);

            try {
                TimeUnit.SECONDS.sleep(5);
            }
            catch (InterruptedException ex) {
                ex.printStackTrace();
                break;
            }
        }
    }

    /** For testing */
    public static PolarClient asDisabled() {
        return new PolarClient(null, null, null);
    }

    public boolean isAvilable() {
        return isAvilable;
    }


    public Optional<PolarLicenseKey> validateLicenseKey(String apiKey) {
        if (!isAvilable)
            return Optional.empty();

        return Optional
                .ofNullable(licenses.get(apiKey))
                .or(() -> fetchLicenseKey(apiKey));
    }

    private Optional<PolarLicenseKey> fetchLicenseKey(String apiKey) {

        String apiPath = "/v1/license-keys/validate";

        Map<String, Object> request = Map.of(
                "organization_id", orgId,
                "key", apiKey
        );

        String body = gson.toJson(request);

        URI uri = URI.create(baseUri + apiPath);

        HttpRequest req = HttpRequest.newBuilder(uri)
                .header("Authorization", "bearer " + accessToken)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(5))
                .build();

        try {
            var rsp = client.send(req, HttpResponse.BodyHandlers.ofString());

            if (rsp.statusCode() == 200) {
                Map<String, Object> rspDecoded = gson.fromJson(rsp.body(), Map.class);

                String customerId = (String) rspDecoded.get("customer_id");
                String benefitId = (String) rspDecoded.get("benefit_id");
                String status = (String) rspDecoded.get("status");

                var ret  = new PolarLicenseKey(apiKey, customerId, benefitId, status, Instant.now());
                licenses.put(apiKey, ret);
                return Optional.of(ret);
            }
            else {
                logger.error("Bad status code form polar API: {}", rsp.statusCode());
                return Optional.empty();
            }

        } catch (InterruptedException | IOException ex) {
            return Optional.empty();
        }
    }

    public void reportKeyUse(String apiKey, Instant snapshotTime, int usage, int overusage) {
        if (!isAvilable) return;
        if (usage == 0 && overusage == 0) return;

        PolarLicenseKey license = licenses.get(apiKey);
        if (license == null) {
            logger.error("Could not report usage, unknown API key: " + apiKey);
        }

        String apiPath = "/v1/events/ingest";
        String ts = snapshotTime
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        Map<String, Object> request = Map.of(
                "key", apiKey,
                "events", List.of(
                        Map.of(
                                "name", "api_use",
                                "timestamp", ts,
                                "customer_id", license.customerId(),
                                "metadata",
                                    Map.of("query_use", usage,
                                            "daily_limit_overusage", overusage)
                        ))
        );

        URI uri = URI.create(baseUri + apiPath);

        HttpRequest req = HttpRequest.newBuilder(uri)
                .header("Authorization", "bearer " + accessToken)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(request)))
                .timeout(Duration.ofSeconds(5))
                .build();

        try {
            var rsp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (rsp.statusCode() != 200) {
                logger.error("Bad status code form polar API: {}", rsp.statusCode());
            }
        } catch (InterruptedException | IOException ex) {
            logger.error("Error in talking to polar API", ex);
        }
    }

    public void stop() {
        revalidationJob.cancel(true);
        client.shutdownNow();
    }
}
