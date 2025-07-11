package nu.marginalia.actor.proc;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.WmsaHome;
import nu.marginalia.actor.prototype.RecordActorPrototype;
import nu.marginalia.actor.state.ActorResumeBehavior;
import nu.marginalia.actor.state.ActorStep;
import nu.marginalia.actor.state.Resume;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.nodecfg.NodeConfigurationService;
import nu.marginalia.nodecfg.model.NodeProfile;
import nu.marginalia.service.control.ServiceEventLog;
import nu.marginalia.service.module.ServiceConfiguration;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Singleton
public class ScrapeFeedsActor extends RecordActorPrototype {
    private static final Logger logger = LoggerFactory.getLogger(ScrapeFeedsActor.class);

    private final Duration pollInterval = Duration.ofHours(6);

    private final ServiceEventLog eventLog;
    private final NodeConfigurationService nodeConfigurationService;
    private final HikariDataSource dataSource;
    private final int nodeId;

    private final Path feedPath = WmsaHome.getHomePath().resolve("data/scrape-urls.txt");

    private static boolean insertFoundDomains = Boolean.getBoolean("loader.insertFoundDomains");

    public record Initial() implements ActorStep {}
    @Resume(behavior = ActorResumeBehavior.RETRY)
    public record Wait(String ts) implements ActorStep {}
    @Resume(behavior = ActorResumeBehavior.RESTART)
    public record Scrape() implements ActorStep {}

    @Override
    public ActorStep transition(ActorStep self) throws Exception {
        return switch(self) {
            case Initial() -> {
                if (!insertFoundDomains) yield new Error("Domain insertion prohibited, aborting");

                if (nodeConfigurationService.get(nodeId).profile() != NodeProfile.REALTIME) {
                    yield new Error("Invalid node profile for RSS update");
                }
                else {
                    yield new Wait(LocalDateTime.now().toString());
                }
            }
            case Wait(String untilTs) -> {
                var until = LocalDateTime.parse(untilTs);
                var now = LocalDateTime.now();

                long remaining = Duration.between(now, until).toMillis();

                if (remaining > 0) {
                    Thread.sleep(remaining);
                    yield new Wait(untilTs);
                }
                else {
                    yield new Scrape();
                }
            }
            case Scrape() -> {
                if (!Files.exists(feedPath)) {
                    eventLog.logEvent("ScrapeFeedsActor", "No feed file found in " + feedPath + " , disabling actor");
                    yield new End();
                }

                for (String url : Files.readAllLines(feedPath)) {
                    if (url.isBlank() || url.startsWith("#")) continue;

                    try {
                        scrapeLinks(URI.create(url));
                    }
                    catch (IllegalArgumentException ex) {
                        eventLog.logEvent("ScrapeFeedsActor", "Failed to parse URL " + url);
                    }
                    catch (IOException | InterruptedException | SQLException ex) {
                        eventLog.logEvent("ScrapeFeedsActor", "Failed to fetch domains from " + url + " - " + ex.getMessage());
                    }
                }

                yield new Wait(LocalDateTime.now().plus(pollInterval).toString());
            }
            default -> new Error();
        };
    }

    /** Scrape the links from the given URL and insert unseen domains into the database */
    private void scrapeLinks(URI domainsUrl) throws SQLException, IOException, InterruptedException {
        try (HttpClient client = HttpClient.newBuilder().build()) {
            var httpReq = HttpRequest.newBuilder(domainsUrl).GET().build();

            HttpResponse<String> result = client.send(httpReq, HttpResponse.BodyHandlers.ofString());
            if (result.statusCode() != 200) {
                eventLog.logEvent("ScrapeFeedsActor", "Failed to fetch domains from " + domainsUrl + " - status code " + result.statusCode());
                return;
            }
            Optional<String> ct = result.headers().firstValue("Content-Type");
            if (ct.isEmpty()) {
                eventLog.logEvent("ScrapeFeedsActor", "Failed to fetch domains from " + domainsUrl + " - no content type");
            }

            Set<EdgeDomain> validDomains = new HashSet<>();

            for (Element e : Jsoup.parse(result.body()).select("a")) {
                String s = e.attr("href");
                if (s.isBlank()) continue;
                if (!s.contains("://")) continue;

                URI uri = URI.create(s);
                String scheme = uri.getScheme();
                String host = uri.getHost();

                if (scheme == null || host == null)
                    continue;
                if (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))
                    continue;

                validDomains.add(new EdgeDomain(host));
            }

            insertDomains(validDomains, 0 /* node affinity = 0 means the next index node to run grabs these domains */);

            eventLog.logEvent("ScrapeFeedsActor", "Polled " + domainsUrl + " for new domains");
        }
    }

    /** Insert the given domains into the database, updating the node affinity if the domain already exists */
    private void insertDomains(Collection<EdgeDomain> domains, int node) throws SQLException {

        // Insert the domains into the database, updating the node affinity if the domain already exists and the affinity is not already set to a node
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                        INSERT INTO EC_DOMAIN (DOMAIN_NAME, DOMAIN_TOP, NODE_AFFINITY)
                        VALUES (?, ?, ?)
                        ON DUPLICATE KEY UPDATE NODE_AFFINITY = IF(NODE_AFFINITY<=0, VALUES(NODE_AFFINITY), NODE_AFFINITY)
                        """))
        {
            for (var domain : domains) {
                logger.info("Inserting domain {} into the database", domain);

                stmt.setString(1, domain.toString());
                stmt.setString(2, domain.getTopDomain());
                stmt.setInt(3, node);
                stmt.addBatch();
            }
            stmt.executeBatch();

            if (!conn.getAutoCommit()) {
                conn.commit();
            }
        }
    }

    @Override
    public String describe() {
        return "Periodically scrape an address for new domains and assign them to be crawled";
    }

    @Inject
    public ScrapeFeedsActor(Gson gson,
                            ServiceEventLog eventLog,
                            ServiceConfiguration configuration,
                            NodeConfigurationService nodeConfigurationService,
                            HikariDataSource dataSource)
    {
        super(gson);
        this.eventLog = eventLog;
        this.nodeConfigurationService = nodeConfigurationService;
        this.dataSource = dataSource;
        this.nodeId = configuration.node();
    }

}
