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
    private final Map<String, DDGTDomain> topDomains = new HashMap<>();
    private final Map<String, DDGTDomain> domains = new HashMap<>();

    private final Gson gson = GsonFactory.get();

    private static final Logger logger = LoggerFactory.getLogger(DDGTrackerData.class);

    public DDGTrackerData() {
        Path dataDir = WmsaHome.getDataPath().resolve("tracker-radar");
        if (!Files.exists(dataDir)) {
            logger.info("tracker-radar data absent from expected path {}, loading nothing", dataDir);
            return;
        }

        try (var sources = Files.list(dataDir.resolve("domains"))) {
            sources.filter(Files::isDirectory).forEach(this::loadDomainDir);
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
        try (var dirContent = Files.list(dir)) {
            dirContent
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .forEach(this::loadDomain);
        }
        catch (IOException e) {
            logger.error("Error while loading DDGT tracker data", e);
        }
    }

    void loadDomain(Path jsonFile) {
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

    public Set<String> getAllClassifications() {
        Set<String> ret = new HashSet<>();
        for (var domain: domains.values()) {
            ret.addAll(domain.categories());
        }
        return ret;
    }
}
