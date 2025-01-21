package nu.marginalia.index.results;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import gnu.trove.map.hash.TIntDoubleHashMap;
import nu.marginalia.WmsaHome;
import nu.marginalia.db.DbDomainQueries;
import nu.marginalia.model.EdgeDomain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.TimeUnit;

@Singleton
public class DomainRankingOverrides {
    private final DbDomainQueries domainQueries;

    private volatile TIntDoubleHashMap rankingFactors = new TIntDoubleHashMap(100, 0.75f, -1, 1.);

    private static final Logger logger = LoggerFactory.getLogger(DomainRankingOverrides.class);

    private final Path overrideFilePath;

    @Inject
    public DomainRankingOverrides(DbDomainQueries domainQueries) {
        this.domainQueries = domainQueries;

        overrideFilePath = WmsaHome.getDataPath().resolve("domain-ranking-factors.txt");

        Thread.ofPlatform().start(this::updateRunner);
    }

    // for test access
    public DomainRankingOverrides(DbDomainQueries domainQueries, Path overrideFilePath)
    {
        this.domainQueries = domainQueries;
        this.overrideFilePath = overrideFilePath;
    }


    public double getRankingFactor(int domainId) {
        return rankingFactors.get(domainId);
    }

    private void updateRunner() {
        for (;;) {
            reloadFile();

            try {
                TimeUnit.MINUTES.sleep(5);
            } catch (InterruptedException ex) {
                logger.warn("Thread interrupted", ex);
                break;
            }
        }
    }

    void reloadFile() {
        if (!Files.exists(overrideFilePath)) {
            return;
        }

        try {
            List<String> lines = Files.readAllLines(overrideFilePath);

            double factor = 1.;

            var newRankingFactors = new TIntDoubleHashMap(lines.size(), 0.75f, -1, 1.);

            for (var line : lines) {
                if (line.isBlank()) continue;
                if (line.startsWith("#")) continue;

                String[] parts = line.split("\\s+");
                if (parts.length != 2) {
                    logger.warn("Unrecognized format for domain overrides file: {}", line);
                    continue;
                }

                try {
                    switch (parts[0]) {
                        case "value" -> {
                            // error handle me
                            factor = Double.parseDouble(parts[1]);
                            if (factor < 0) {
                                logger.error("Negative values are not permitted, found {}", factor);
                                factor = 1;
                            }
                        }
                        case "domain" -> {
                            // error handle
                            OptionalInt domainId = domainQueries.tryGetDomainId(new EdgeDomain(parts[1]));
                            if (domainId.isPresent()) {
                                newRankingFactors.put(domainId.getAsInt(), factor);
                            }
                            else {
                                logger.warn("Unrecognized domain id {}", parts[1]);
                            }
                        }
                        default -> {
                            logger.warn("Unrecognized format {}", line);
                        }
                    }
                } catch (Exception ex) {
                    logger.warn("Error in parsing domain overrides file: {} ({})", line, ex.getClass().getSimpleName());
                }
            }

            rankingFactors = newRankingFactors;
        } catch (IOException ex) {
            logger.error("Failed to read " + overrideFilePath, ex);
        }
    }
}
