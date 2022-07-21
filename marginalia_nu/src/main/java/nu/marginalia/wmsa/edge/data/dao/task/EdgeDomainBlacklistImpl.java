package nu.marginalia.wmsa.edge.data.dao.task;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
import gnu.trove.set.hash.TIntHashSet;
import io.prometheus.client.Counter;
import io.reactivex.rxjava3.schedulers.Schedulers;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

@Singleton
public class EdgeDomainBlacklistImpl implements EdgeDomainBlacklist {
    private volatile TIntHashSet spamDomainSet = new TIntHashSet();
    private final HikariDataSource dataSource;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static final Counter wmsa_blacklist_intercept = Counter.build("wmsa_blacklist_intercept",
            "wmsa_blacklist_intercept").register();
    @Inject
    public EdgeDomainBlacklistImpl(HikariDataSource dataSource) {
        this.dataSource = dataSource;

        Schedulers.io().schedulePeriodicallyDirect(this::updateSpamList, 5, 600, TimeUnit.SECONDS);

        updateSpamList();
    }

    private void updateSpamList() {
        try {
            int oldSetSize = spamDomainSet.size();

            spamDomainSet = getSpamDomains();

            if (oldSetSize == 0) {
                logger.info("Synchronized {} spam domains", spamDomainSet.size());
            }
        }
        catch (Exception ex) {
            logger.error("Failed to synchronize spam domains", ex);
        }
    }


    @SneakyThrows
    public TIntHashSet getSpamDomains() {
        final TIntHashSet result = new TIntHashSet(1_000_000);

        try (var connection = dataSource.getConnection()) {
            try (var stmt = connection.prepareStatement("SELECT EC_DOMAIN.ID FROM EC_DOMAIN INNER JOIN EC_DOMAIN_BLACKLIST ON EC_DOMAIN_BLACKLIST.URL_DOMAIN = EC_DOMAIN.DOMAIN_TOP")) {
                stmt.setFetchSize(1000);
                var rsp = stmt.executeQuery();
                while (rsp.next()) {
                    result.add(rsp.getInt(1));
                }
            }
        }

        return result;
    }

    @Override
    public boolean isBlacklisted(int domainId) {
        if (spamDomainSet.contains(domainId)) {
            wmsa_blacklist_intercept.inc();
            return true;
        }

        return false;
    }

}
