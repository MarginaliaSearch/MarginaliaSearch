package nu.marginalia.api.polar;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import jakarta.inject.Named;
import nu.marginalia.api.model.ApiLicense;
import nu.marginalia.model.gson.GsonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.*;
import java.time.format.DateTimeFormatter;
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
    private final String overuseMeterId;

    private ConcurrentHashMap<String, PolarLicenseKey> licenses = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, PolarSubscription> subcriptions = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, Long> apiOveruseForPeriod = new ConcurrentHashMap<>();

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
            this.overuseMeterId = null;
            this.revalidationJob = null;
        }
        else {
            logger.info("Polar integration enabled!");

            this.client = java.net.http.HttpClient.newBuilder()
                    .executor(virtualExecutor)
                    .build();

            var overuseMeterIdMaybe = fetchMeterId("API Daily Limit Excess");
            if (overuseMeterIdMaybe.isEmpty()) {
                logger.error("Could not fetch daily API limit excess meter ID");
                this.overuseMeterId = null;
                this.isAvilable = false;
            }
            else {
                this.overuseMeterId = overuseMeterIdMaybe.get();
                this.isAvilable = true;
            }

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

                var licenseKey = new PolarLicenseKey(apiKey, customerId, benefitId, status, Instant.now());
                licenses.put(apiKey, licenseKey);

                var sub = fetchSubscription(licenseKey);

                if (sub.isPresent()) {
                    subcriptions.put(apiKey, sub.get());

                    var apiOveruse = fetchMeterReading(overuseMeterId, sub.get());
                    if (apiOveruse.isPresent()) {
                        apiOveruseForPeriod.putIfAbsent(apiKey, apiOveruse.getAsLong());
                    }

                } else {
                    logger.info("No subscription fund for {}", apiKey);
                }

                return Optional.of(licenseKey);
            }
            else {
                logger.error("Bad status code form polar API: {} {}", rsp.statusCode(), rsp.body());
                return Optional.empty();
            }

        } catch (InterruptedException | IOException ex) {
            logger.error("Error communicating with polar API", ex);
            return Optional.empty();
        }
    }

    public Optional<PolarSubscription> fetchSubscription(PolarLicenseKey key) {

        String apiPath = "/v1/subscriptions/?organization_id=%s&customer_id=%s&active=true"
                .formatted(orgId, key.customerId());

        URI uri = URI.create(baseUri + apiPath);

        HttpRequest req = HttpRequest.newBuilder(uri)
                .header("Authorization", "bearer " + accessToken)
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build();

        try {
            var rsp = client.send(req, HttpResponse.BodyHandlers.ofString());

            if (rsp.statusCode() == 200) {
                String rspRaw = rsp.body();
                PolarSubscriptionApiRspModel rspDecoded = gson.fromJson(rspRaw, PolarSubscriptionApiRspModel.class);

                System.out.println(rspDecoded);
                for (var item : rspDecoded.items()) {
                    Instant currentPeriodStart = Instant.parse(item.current_period_start());
                    Instant currentPeriodEnd = Instant.parse(item.current_period_end());
                    String status = item.status();

                    if (!item.hasBenefitId(key.benefitId()))
                        continue;

                    return Optional.of(
                            new PolarSubscription(
                                    key.apiKey(),
                                    key.customerId(),
                                    status,
                                    Instant.now(),
                                    currentPeriodStart,
                                    currentPeriodEnd
                            )
                    );
                }
            }
            else {
                logger.error("Bad status code form polar API: {} {}", rsp.statusCode(), rsp.body());
            }
        } catch (InterruptedException | IOException ex) {
            logger.error("Error communicating with polar API", ex);
        }

        return Optional.empty();
    }

    public Optional<String> fetchMeterId(String name) {

        String apiPath = "/v1/meters/?organization_id=%s&query=%s&is_archived=false"
                .formatted(orgId, URLEncoder.encode(name));

        URI uri = URI.create(baseUri + apiPath);

        HttpRequest req = HttpRequest.newBuilder(uri)
                .header("Authorization", "bearer " + accessToken)
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build();

        List<PolarSubscription> ret = new ArrayList<>();

        try {
            var rsp = client.send(req, HttpResponse.BodyHandlers.ofString());

            if (rsp.statusCode() == 200) {
                String rspRaw = rsp.body();
                PolarMetersApiListRspModel model = gson.fromJson(rspRaw, PolarMetersApiListRspModel.class);

                if (model.items().isEmpty())
                    return Optional.empty();
                return Optional.of(model.items().getFirst().id());
            }
            else {
                logger.error("Bad status code form polar API: {} {}", rsp.statusCode(), rsp.body());
            }
        } catch (InterruptedException | IOException ex) {
            logger.error("Error communicating with polar API", ex);
        }

        return Optional.empty();
    }

    /** Read the provided meter usage for the current billing period, from subscription. */
    public OptionalLong fetchMeterReading(String meterId, PolarSubscription subscription) {

        String apiPath = "/v1/meters/%s/quantities?customer_id=%s&start_timestamp=%s&end_timestamp=%s&interval=day"
                .formatted(meterId,
                        subscription.customerId(),
                        subscription.currentPeriodStart(),
                        subscription.currentPeriodEnd());

        URI uri = URI.create(baseUri + apiPath);

        HttpRequest req = HttpRequest.newBuilder(uri)
                .header("Authorization", "bearer " + accessToken)
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build();

        List<PolarSubscription> ret = new ArrayList<>();

        try {
            var rsp = client.send(req, HttpResponse.BodyHandlers.ofString());

            if (rsp.statusCode() == 200) {
                return OptionalLong.of(gson.fromJson(rsp.body(), PolarMeterQuantityApiRspModel.class).total());
            }
            else {
                logger.error("Bad status code form polar API: {} {}", rsp.statusCode(), rsp.body());
            }
        } catch (InterruptedException | IOException ex) {
            logger.error("Error communicating with polar API", ex);
        }

        return OptionalLong.empty();
    }

    /** Report API key usage to the Polar.SH API via events, then fetch the updated meter values
     * and insert into apiOveruseForPeriod so that we have a client side view of billable requests
     * */
    public void reportKeyUse(String apiKey, Instant snapshotTime, int usage, int overusage) {
        if (!isAvilable) return;
        if (usage == 0 && overusage == 0) return;

        PolarLicenseKey license = licenses.get(apiKey);
        if (license == null) {
            logger.error("Could not report usage, unknown API key: " + apiKey);
            return;
        }

        PolarSubscription subscription = subcriptions.get(apiKey);
        if (subscription == null) {
            logger.error("Could not report usage, no subscription for API key: " + apiKey);
            return;
        }

        String apiPath = "/v1/events/ingest";
        String ts = snapshotTime
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        Map<String, Object> request = Map.of(
                "key", apiKey,
                "events", List.of(
                        Map.of(
                                "name", "api_query",
                                "timestamp", ts,
                                "customer_id", license.customerId(),
                                "metadata",
                                    Map.of("api_query_use", usage,
                                            "api_query_excess", overusage)
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
                logger.error("Bad status code form polar API: {} {}", rsp.statusCode(), rsp.body());
            }
        } catch (InterruptedException | IOException ex) {
            logger.error("Error in talking to polar API", ex);
        }

        var meterUse = fetchMeterReading(overuseMeterId, subscription);
        if (meterUse.isPresent()) {
            apiOveruseForPeriod.put(apiKey, meterUse.getAsLong());
        }
        else {
            logger.error("Failed to get API use meter reading, falling back to estimate");

            // In case of an outage, we'll estimate that the changes went through until we can get
            // a solid reading
            apiOveruseForPeriod.merge(apiKey, (long) overusage, Long::sum);
        }
    }

    public OptionalLong getApiOveruseEstimate(ApiLicense license) {
        Long val = apiOveruseForPeriod.get(license.key());
        if (val == null) {
            return OptionalLong.empty();
        } else {
            return OptionalLong.of(val);
        }
    }

    public void stop() {
        revalidationJob.cancel(true);
        client.shutdownNow();
    }
}

// https://polar.sh/docs/api-reference/subscriptions/list

record PolarSubscriptionApiRspModel(List<PolarSubscriptionApiItemModel> items) {}
record PolarSubscriptionApiItemModel(String status,
                                     String current_period_start,
                                     String current_period_end,
                                     PolarSubscriptionApiProductModel product
                                     ) {

    public boolean hasBenefitId(String benefitId) {
        if (product == null || product.benefits() == null)
            return false;

        return product.benefits().stream().anyMatch(ben -> benefitId.equals(ben.id()));
    }
}
record PolarSubscriptionApiProductModel(List<PolarSubscriptionApiBenefitModel> benefits) {}
record PolarSubscriptionApiBenefitModel(String id) {}

// https://polar.sh/docs/api-reference/meters/list
record PolarMetersApiListRspModel(List<PolarMetersApiMeterModel> items) {}
record PolarMetersApiMeterModel(String name, String id) {}

// https://polar.sh/docs/api-reference/meters/get-quantities
record PolarMeterQuantityApiRspModel(long total) {}