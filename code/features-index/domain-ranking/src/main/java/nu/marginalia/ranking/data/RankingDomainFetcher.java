package nu.marginalia.ranking.data;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.db.DomainBlacklistImpl;
import nu.marginalia.model.crawl.DomainIndexingState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

@Singleton
public class RankingDomainFetcher {
    protected final HikariDataSource dataSource;
    protected final DomainBlacklistImpl blacklist;
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected boolean getNames = false;

    @Inject
    public RankingDomainFetcher(HikariDataSource dataSource, DomainBlacklistImpl blacklist) {
        this.dataSource = dataSource;
        this.blacklist = blacklist;
    }

    public void retainNames() {
        this.getNames = true;
    }

    public void getDomains(Consumer<RankingDomainData> consumer) {
        String query;
        if (getNames) {
            query = "SELECT EC_DOMAIN.ID,DOMAIN_NAME,DOMAIN_ALIAS,STATE,KNOWN_URLS FROM EC_DOMAIN INNER JOIN DOMAIN_METADATA ON EC_DOMAIN.ID=DOMAIN_METADATA.ID INNER JOIN EC_DOMAIN_LINK ON SOURCE_DOMAIN_ID=EC_DOMAIN.ID WHERE ((INDEXED>1 AND IS_ALIVE) OR (INDEXED=1 AND VISITED_URLS=KNOWN_URLS AND GOOD_URLS>0)) AND SOURCE_DOMAIN_ID!=DEST_DOMAIN_ID GROUP BY EC_DOMAIN.ID";
        }
        else {
            query = "SELECT EC_DOMAIN.ID,\"\",DOMAIN_ALIAS,STATE,KNOWN_URLS FROM EC_DOMAIN INNER JOIN DOMAIN_METADATA ON EC_DOMAIN.ID=DOMAIN_METADATA.ID INNER JOIN EC_DOMAIN_LINK ON SOURCE_DOMAIN_ID=EC_DOMAIN.ID WHERE ((INDEXED>1 AND IS_ALIVE) OR (INDEXED=1 AND VISITED_URLS=KNOWN_URLS AND GOOD_URLS>0)) AND SOURCE_DOMAIN_ID!=DEST_DOMAIN_ID GROUP BY EC_DOMAIN.ID";
        }

        getDomains(query, consumer);
    }


    public void getPeripheralDomains(Consumer<RankingDomainData> consumer) {
        String query;
        if (getNames) {
            query = "SELECT EC_DOMAIN.ID,DOMAIN_NAME,DOMAIN_ALIAS,STATE,KNOWN_URLS FROM EC_DOMAIN INNER JOIN DOMAIN_METADATA ON EC_DOMAIN.ID=DOMAIN_METADATA.ID  LEFT JOIN EC_DOMAIN_LINK ON SOURCE_DOMAIN_ID=EC_DOMAIN.ID WHERE ((INDEXED>1 AND IS_ALIVE) OR (INDEXED=1 AND VISITED_URLS=KNOWN_URLS AND GOOD_URLS>0)) AND EC_DOMAIN_LINK.ID IS NULL GROUP BY EC_DOMAIN.ID";
        }
        else {
            query = "SELECT EC_DOMAIN.ID,\"\",DOMAIN_ALIAS,STATE,KNOWN_URLS FROM EC_DOMAIN INNER JOIN DOMAIN_METADATA ON EC_DOMAIN.ID=DOMAIN_METADATA.ID  LEFT JOIN EC_DOMAIN_LINK ON SOURCE_DOMAIN_ID=EC_DOMAIN.ID WHERE ((INDEXED>1 AND IS_ALIVE) OR (INDEXED=1 AND VISITED_URLS=KNOWN_URLS AND GOOD_URLS>0)) AND EC_DOMAIN_LINK.ID IS NULL GROUP BY EC_DOMAIN.ID";
        }

        getDomains(query, consumer);
    }

    protected void getDomains(String query, Consumer<RankingDomainData> consumer) {
        try (var conn = dataSource.getConnection(); var stmt = conn.prepareStatement(query)) {
            stmt.setFetchSize(10000);
            var rsp = stmt.executeQuery();
            while (rsp.next()) {
                int id = rsp.getInt(1);
                if (!blacklist.isBlacklisted(id)) {
                    consumer.accept(
                            new RankingDomainData(id,
                                    rsp.getString(2),
                                    rsp.getInt(3),
                                    DomainIndexingState.valueOf(rsp.getString(4)),
                                    rsp.getInt(5)));
                }
            }
        }
        catch (SQLException ex) {
            logger.error("Failed to fetch domains", ex);
        }
    }

    public void eachDomainLink(DomainLinkConsumer consumer) {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("SELECT SOURCE_DOMAIN_ID, DEST_DOMAIN_ID FROM EC_DOMAIN_LINK"))
        {
            stmt.setFetchSize(10000);

            var rsp = stmt.executeQuery();

            while (rsp.next()) {
                int src = rsp.getInt(1);
                int dst = rsp.getInt(2);

                consumer.accept(src, dst);
            }
        }
        catch (SQLException ex) {
            logger.error("Failed to fetch domain links", ex);
        }
    }

    public void domainsByPattern(String pattern, IntConsumer idConsumer) {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
                                                                    // This is sourced from a config file --v
            var rsp = stmt.executeQuery("SELECT ID FROM EC_DOMAIN WHERE DOMAIN_NAME LIKE '" + pattern + "'");
            while (rsp.next()) {
                idConsumer.accept(rsp.getInt(1));
            }
        }
        catch (SQLException ex) {
            logger.error("Failed to fetch domains by pattern", ex);
        }
    }

    public interface DomainLinkConsumer {
        void accept(int from, int to);
    }
}
