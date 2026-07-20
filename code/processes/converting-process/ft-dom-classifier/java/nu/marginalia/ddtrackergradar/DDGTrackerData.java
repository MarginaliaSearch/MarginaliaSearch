package nu.marginalia.ddtrackergradar;

import com.google.gson.Gson;
import nu.marginalia.WmsaHome;
import nu.marginalia.ddtrackergradar.model.DDGTDomain;
import nu.marginalia.model.gson.GsonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Holds tracker metadata from DuckDuckGo's Tracker Radar
 *  data itself CC-BY-NC-SA 4.0
 * */
public class DDGTrackerData {
    private volatile Map<String, DDGTDomain> topDomains = Map.of();
    private volatile Map<String, DDGTDomain> domains = Map.of();
    private volatile boolean isLoaded = false;

    private final Gson gson = GsonFactory.get();

    private static final Logger logger = LoggerFactory.getLogger(DDGTrackerData.class);

    public DDGTrackerData() {
        Thread.ofPlatform().daemon().name("DDGTrackerData Loader").start(this::loadData);
    }


    public boolean isLoaded() {
        return isLoaded;
    }

    private void loadData() {

        // Data is assumed to be in ${WMSA_HOME}/data/tracker-radar
        // ... do a shallow clone of the repo
        // https://github.com/duckduckgo/tracker-radar/

        Path dataDir = WmsaHome.getDataPath().resolve("tracker-radar");
        if (!Files.exists(dataDir)) {
            logger.info("tracker-radar data absent from expected path {}, loading nothing", dataDir);
            return;
        }

        long startTime = System.currentTimeMillis();

        Map<String, DDGTDomain> newTopDomains = new HashMap<>();
        Map<String, DDGTDomain> newDomains = new HashMap<>();

        try (var sources = Files.list(dataDir.resolve("domains"))) {
            sources.filter(Files::isDirectory).forEach(dir -> loadDomainDir(dir, newTopDomains, newDomains));

            topDomains = newTopDomains;
            domains = newDomains;
            isLoaded = true;

            logger.info("Loaded tracker-radar data in {} ms", System.currentTimeMillis() - startTime);
        }
        catch (IOException e) {
            logger.error("Failed to read tracker radar data dir", e);
        }
    }

    /** Tries to fetch available information about tracking coming from the specified domain
     */
    public Optional<DDGTDomain> getDomainInfo(String domain) {
        return Optional
                .ofNullable(topDomains.get(domain))
                .or(() -> Optional.ofNullable(domains.get(domain)));
    }

    /** public for testing */
    public void loadDomainDir(Path dir) {
        Map<String, DDGTDomain> newTopDomains = new HashMap<>(topDomains);
        Map<String, DDGTDomain> newDomains = new HashMap<>(domains);

        loadDomainDir(dir, newTopDomains, newDomains);

        topDomains = newTopDomains;
        domains = newDomains;
        isLoaded = true;
    }

    private void loadDomainDir(Path dir,
                               Map<String, DDGTDomain> topDomains,
                               Map<String, DDGTDomain> domains) {
        try (var dirContent = Files.list(dir)) {
            dirContent
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .forEach(file -> loadDomainModel(file, topDomains, domains));
        }
        catch (IOException e) {
            logger.error("Error while loading DDGT tracker data", e);
        }
    }

    private void loadDomainModel(Path jsonFile,
                                 Map<String, DDGTDomain> topDomains,
                                 Map<String, DDGTDomain> domains) {
        try {
            var model = gson.fromJson(Files.readString(jsonFile), DDGTDomain.class);

            if (model.domain() == null)
                return;
            if ((model.owner() == null || model.owner().isEmpty())
             && (model.categories() == null || model.categories().isEmpty()))
                return;

            topDomains.put(model.domain(), model);
            domains.put(model.domain(), model);

            if (model.subdomains() != null) {
                for (String subdomain : model.subdomains()) {
                    domains.put(subdomain + "." + model.domain(), model);
                }
            }
        }
        catch (Exception e) {
            logger.error("Error while loading DDGT tracker data", e);
        }
    }

    // Export all classifications in the data set
    public Set<String> getAllClassifications() {
        Set<String> ret = new HashSet<>();
        for (var domain: domains.values()) {
            ret.addAll(domain.categories());
        }
        return ret;
    }
}
