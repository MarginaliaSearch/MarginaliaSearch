package nu.marginalia.wmsa.edge.data.dao.task;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import io.prometheus.client.Gauge;
import io.reactivex.rxjava3.schedulers.Schedulers;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.configuration.server.Initialization;
import nu.marginalia.wmsa.edge.data.dao.EdgeDataStoreDaoImpl;
import nu.marginalia.wmsa.edge.data.dao.EdgeDataStoreTaskDao;
import nu.marginalia.wmsa.edge.model.*;
import nu.marginalia.wmsa.edge.model.crawl.EdgeDomainIndexingState;
import nu.marginalia.wmsa.edge.model.crawl.EdgeIndexTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static java.sql.Connection.TRANSACTION_READ_COMMITTED;
import static java.sql.Connection.TRANSACTION_READ_UNCOMMITTED;

public class EdgeDataStoreTaskDaoImpl implements EdgeDataStoreTaskDao {

    private final HikariDataSource dataSource;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private static final Gauge wmsa_index_task_quality = Gauge.build("wmsa_discover_index_task_quality", "wmsa_discover_index_task_quality").labelNames("depth").register();

    private final LinkedBlockingQueue<EdgeDomainDiscoverTask> discoverDomainQueue = new LinkedBlockingQueue<>(200);
    private final LinkedBlockingQueue<EdgeDomainDiscoverTask> indexDomainQueue1 = new LinkedBlockingQueue<>(200);

    private final EdgeDomainBlacklist blacklist;
    private final EdgeDataStoreTaskTuner taskQueryTuner;
    private final EdgeDataStoreDaoImpl baseDao;
    private final EdgeDataStoreTaskOngoingJobs ongoingJobs;
    private final EdgeFinishTasksQueue finishTasksQueue;
    private final Initialization initialization;
    private final Semaphore taskFetchSem = new Semaphore(3, true);
    private final LinkedBlockingDeque<Object> blockingJobs = new LinkedBlockingDeque<>();


    @Inject
    public EdgeDataStoreTaskDaoImpl(HikariDataSource dataSource,
                                    EdgeDomainBlacklist blacklist,
                                    EdgeDataStoreTaskTuner taskQueryTuner,
                                    EdgeDataStoreTaskOngoingJobs ongoingJobs,
                                    EdgeFinishTasksQueue finishTasksQueue,
                                    Initialization initialization)
    {
        this.dataSource = dataSource;
        baseDao = new EdgeDataStoreDaoImpl(dataSource);
        this.blacklist = blacklist;
        this.taskQueryTuner = taskQueryTuner;
        this.ongoingJobs = ongoingJobs;
        this.finishTasksQueue = finishTasksQueue;
        this.initialization = initialization;

        Schedulers.io().schedulePeriodicallyDirect(this::repopulateUrlLinkDensity, 7, 360, TimeUnit.MINUTES);
//        Schedulers.io().schedulePeriodicallyDirect(this::blacklistLinkfarms, 60, 600, TimeUnit.SECONDS);

        var updateDiscoverQueue = new Thread(this::updateDiscoverQueue, "UpdateDiscoverQueue");
        updateDiscoverQueue.setDaemon(true);
        updateDiscoverQueue.start();

        var updateIndexQueue = new Thread(this::updateIndexQueue, "UpdateIndexQueue");
        updateIndexQueue.setDaemon(true);
        updateIndexQueue.start();

    }

    @Override
    public boolean isBlocked() {
        return !blockingJobs.isEmpty();
    }


    @SneakyThrows
    private void blacklistLinkfarms() {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);

            List<Integer> ids = new ArrayList<>(1000);

            try (var stmt = connection.prepareStatement("SELECT SQL_BUFFER_RESULT URL_TOP_DOMAIN_ID from EC_DOMAIN USE INDEX(EC_DOMAIN_ID_INDEXED_INDEX) WHERE INDEXED>=1 GROUP BY URL_TOP_DOMAIN_ID HAVING COUNT(ID)>100")) {
                connection.setTransactionIsolation(TRANSACTION_READ_UNCOMMITTED);
                var rsp = stmt.executeQuery();
                while (rsp.next()) {
                    ids.add(rsp.getInt(1));
                }
            }
            catch (Exception ex) {
                logger.error("DB error", ex);
                return;
            }
            finally {
                connection.setTransactionIsolation(TRANSACTION_READ_COMMITTED);
            }

            try (var stmt = connection.prepareStatement("UPDATE EC_TOP_DOMAIN SET ALIVE=0 WHERE ID=?")) {
                for (int id : ids) {
                    stmt.setInt(1, id);
                    stmt.addBatch();
                }
                stmt.executeBatch();
                connection.commit();
            }
            catch (Exception ex) {
                logger.error("DB error", ex);
                connection.rollback();
            }
        }
    }

    public synchronized void clearCaches()
    {
        ongoingJobs.clear();
    }


    @SneakyThrows
    private EdgeIndexTask updateIndexQueue() {

        List<EdgeDomainDiscoverTask> fetchedDomains = new ArrayList<>(100);

        initialization.waitReady();

        for (;;) {
            if (!blockingJobs.isEmpty()) {
                Thread.sleep(1000);
                continue;
            }

            try (var connection = dataSource.getConnection()) {
                try (var stmt =
                             connection.prepareStatement("SELECT ID,URL_PART,IFNULL(RANK,1) FROM EC_DOMAIN USE INDEX(EC_DOMAIN_TRIO) WHERE DOMAIN_ALIAS IS NULL AND STATE=0 AND QUALITY > ? AND INDEXED = 1 ORDER BY QUALITY DESC LIMIT 100")) {

                    stmt.setDouble(1, taskQueryTuner.getIndexQualityLimit());
                    stmt.setFetchSize(100);

                    var rsp = stmt.executeQuery();

                    while (rsp.next()) {
                        int domainId = rsp.getInt(1);
                        var domain = new EdgeDomain(rsp.getString(2));

                        if (blacklist.isBlacklisted(domainId)) {
                            finishBadIndexTask(domain, EdgeDomainIndexingState.BLOCKED);
                            continue;
                        }

                        if (!ongoingJobs.isOngoing(domain)) {
                            fetchedDomains.add(new EdgeDomainDiscoverTask(domain, rsp.getInt(1), rsp.getDouble(3)));
                        }
                    }
                } catch (Exception ex) {
                    logger.error("DB error", ex);
                }
            }

            indexDomainQueue1.removeIf(d -> ongoingJobs.isOngoing(d.domain));

            for (var d : fetchedDomains) {
                if (!blacklist.isBlacklisted(d.id)) {
                    if (!indexDomainQueue1.contains(d) && !ongoingJobs.isOngoing(d.domain)) {
                        indexDomainQueue1.put(d);
                    }
                }
                else {
                    finishBadIndexTask(d.domain, EdgeDomainIndexingState.BLOCKED);
                }
            }
            fetchedDomains.clear();
        }
    }


    @Override
    @SneakyThrows
    public EdgeIndexTask getIndexTask(int pass, int limit) {

        if (!blockingJobs.isEmpty()) {
            return new EdgeIndexTask(null, 0, limit, 1.);
        }
        boolean acquired = taskFetchSem.tryAcquire();
        if (!acquired) {
            return new EdgeIndexTask(null, 0, 1, 1.);
        }

        try {

            if (pass == 1) {
                var task = tryGetIndexTask(0, pass, limit);
                if (task.isPresent()) {
                    return task.get();
                }
            }

            try (var connection = dataSource.getConnection()) {

                for (double adj = 1; adj < 10; adj *= 1.5) {
                    try (var stmt =
                                 connection.prepareStatement("SELECT ID,URL_PART,INDEXED,QUALITY,IFNULL(RANK,1) FROM EC_DOMAIN USE INDEX(EC_DOMAIN_TRIO) WHERE DOMAIN_ALIAS IS NULL AND STATE=0 AND QUALITY > ? AND INDEXED > ? AND INDEXED <= ? ORDER BY QUALITY DESC LIMIT 100")) {
                        stmt.setFetchSize(100);
                        stmt.setDouble(1, taskQueryTuner.getIndexQualityLimit() - (adj - 1) - Math.random());
                        stmt.setInt(2, pass / 10);
                        stmt.setInt(3, pass);
                        var rsp = stmt.executeQuery();
                        while (rsp.next()) {
                            var domain = new EdgeDomain(rsp.getString(2));
                            int domainId = rsp.getInt(1);

                            if (blacklist.isBlacklisted(domainId)) {
                                finishBadIndexTask(domain, EdgeDomainIndexingState.BLOCKED);
                                continue;
                            }

                            if (ongoingJobs.add(domain)) {
                                var task = getUrlsForIndexTask(domain, domainId, rsp.getInt(3), limit, rsp.getDouble(5));

                                if (task.isEmpty()) {
                                    finishBadIndexTask(domain, EdgeDomainIndexingState.EXHAUSTED);
                                } else {
                                    wmsa_index_task_quality
                                            .labels(String.format("%02d", pass))
                                            .set(rsp.getDouble(4));

                                    return task;
                                }
                            }
                        }
                    } catch (Exception ex) {
                        logger.error("DB error", ex);
                    }
                }

                return new EdgeIndexTask(null, 0, limit, 1.);
            }
        }
        finally {
            taskFetchSem.release();
        }
    }

    private Optional<EdgeIndexTask> tryGetIndexTask(int attempt, int pass, int limit) {
        if (attempt > 5) {
            return Optional.of(new EdgeIndexTask(null, 1, limit, 1.));
        }

        var t = indexDomainQueue1.poll();

        if (t != null) {
            if (!validateIndexedState(t.id, 1)) {
                return tryGetIndexTask(attempt + 1, pass, limit);
            }
            if (!ongoingJobs.add(t.domain)) {
                return tryGetIndexTask(attempt + 1, pass, limit);
            }

            var task = getUrlsForIndexTask(t.domain, t.id, pass, limit, t.rank);

            if (task.isEmpty()) {
                finishBadIndexTask(t.domain,  EdgeDomainIndexingState.EXHAUSTED);
                return tryGetIndexTask(attempt + 1, pass, limit);
            }
            return Optional.of(task);
        }
        return Optional.of(new EdgeIndexTask(null, 1, limit, 1.));
    }


    @SneakyThrows
    private EdgeIndexTask updateDiscoverQueue() {

        List<EdgeDomainDiscoverTask> fetchedDomains = new ArrayList<>(100);

        initialization.waitReady();

        for (;;) {
            if (!blockingJobs.isEmpty()) {
                Thread.sleep(1000);
                continue;
            }

            try (var connection = dataSource.getConnection()) {
                try (var stmt =
                             connection.prepareStatement("SELECT EC_DOMAIN.ID,EC_DOMAIN.URL_PART,IFNULL(RANK, 1) FROM EC_DOMAIN USE INDEX(EC_DOMAIN_TRIO) INNER JOIN EC_TOP_DOMAIN ON EC_TOP_DOMAIN.ID=URL_TOP_DOMAIN_ID WHERE DOMAIN_ALIAS IS NULL AND STATE=0 AND QUALITY > ? AND INDEXED = 0 AND ALIVE = 1 ORDER BY QUALITY DESC LIMIT 100")) {

                    stmt.setDouble(1, taskQueryTuner.getDiscoverQualityLimit());
                    stmt.setFetchSize(100);

                    var rsp = stmt.executeQuery();

                    while (rsp.next()) {
                        var domain = new EdgeDomain(rsp.getString(2));

                        if (!ongoingJobs.isOngoing(domain)) {
                            fetchedDomains.add(new EdgeDomainDiscoverTask(domain, rsp.getInt(1), rsp.getDouble(3)));
                        }
                    }
                } catch (Exception ex) {
                    logger.error("DB error", ex);
                }
            }

            discoverDomainQueue.removeIf(d -> ongoingJobs.isOngoing(d.domain));

            for (var d : fetchedDomains) {
                if (!blacklist.isBlacklisted(d.id)) {
                    if (!discoverDomainQueue.contains(d) && !ongoingJobs.isOngoing(d.domain)) {
                        discoverDomainQueue.put(d);
                    }
                }
                else {
                    finishIndexTask(d.domain, -1000, EdgeDomainIndexingState.BLOCKED);
                }
            }
            fetchedDomains.clear();
        }
    }

    @Override
    @SneakyThrows
    public EdgeIndexTask getDiscoverTask() {
        boolean acquired = taskFetchSem.tryAcquire();
        if (!acquired) {
            return new EdgeIndexTask(null, 0, 1, 1.);
        }

        try {

            if (!blockingJobs.isEmpty()) {
                return new EdgeIndexTask(null, 0, 1, 1.);
            }

            return tryGetDiscoverTask(0)
                    .orElseGet(() -> new EdgeIndexTask(null, 0, 1, 1.));
        }
        finally {
            taskFetchSem.release();
        }
    }

    @SneakyThrows
    private Optional<EdgeIndexTask> tryGetDiscoverTask(int attempt) {
        if (attempt > 5) {
            return Optional.empty();
        }
        var t = discoverDomainQueue.poll(50, TimeUnit.MILLISECONDS);

        if (t != null) {
            if (!validateIndexedState(t.id, 0)) {
                return tryGetDiscoverTask(attempt+1);
            }
            if (!ongoingJobs.add(t.domain)) {
                return tryGetDiscoverTask(attempt+1);
            }

            var task = getUrlsForIndexTask(t.domain, t.id, 0, 10, t.rank);
            if (task.isEmpty()) {
                if (task.visited.isEmpty()) {
                    logger.warn("No url for {}", t.domain);
                    var rootUrl = new EdgeUrl("https", t.domain, null, "/");
                    baseDao.putUrl(-5, rootUrl);

                    task.urls.add(rootUrl);
                } else {
                    ongoingJobs.remove(t.domain);
                    return tryGetDiscoverTask(attempt+1);
                }
            }

            return Optional.of(task);
        }
        return Optional.of(new EdgeIndexTask(null, 0, 1, 1.));
    }

    @Override
    @SneakyThrows
    public void finishIndexTask(EdgeDomain domain, double quality, EdgeDomainIndexingState state) {
        finishTasksQueue.add(domain, quality, state);
    }
    @SneakyThrows
    public void finishBadIndexTask(EdgeDomain domain, EdgeDomainIndexingState state) {
        finishTasksQueue.addError(domain, state);
    }

    @Override
    public void flushOngoingJobs() {
        ongoingJobs.clear();
    }

    private void repopulateUrlLinkDensity() {
        try (var connection = dataSource.getConnection();
             var stmt = connection.createStatement()
        ) {
            blockingJobs.push("Repopulate URL Link Density");
            logger.info("Starting link details sync");
            stmt.executeUpdate("INSERT INTO EC_DOMAIN_LINK_AGGREGATE(DOMAIN_ID,LINKS) SELECT DEST_DOMAIN_ID AS ID, 100*SUM(EXP(EC_DOMAIN.QUALITY_RAW))/SQRT(GREATEST(1,COUNT(EC_DOMAIN.ID))) AS LINKS FROM EC_DOMAIN_LINK INNER JOIN EC_DOMAIN ON EC_DOMAIN.ID=EC_DOMAIN_LINK.SOURCE_DOMAIN_ID GROUP BY EC_DOMAIN_LINK.DEST_DOMAIN_ID ON DUPLICATE KEY UPDATE LINKS=VALUES(LINKS)");
            logger.info("Finished link details sync");
        }
        catch (Exception ex) {
            logger.error("DB error", ex);
        }
        finally  {
            blockingJobs.pop();
        }
    }

    private boolean validateIndexedState(int domainId, int expected) {
        try (var connection = dataSource.getConnection()) {

            try (var stmt =
                         connection.prepareStatement("select INDEXED from EC_DOMAIN WHERE ID=?")) {
                stmt.setInt(1, domainId);
                var rsp = stmt.executeQuery();
                if (rsp.next()) {
                    return rsp.getInt(1) == expected;
                }
                else {
                    return false;
                }
            } catch (Exception ex) {
                logger.error("DB error", ex);
            }
        }
        catch (Exception ex) {
            logger.error("DB error", ex);
        }
        return false;

    }

    @SneakyThrows
    private EdgeIndexTask getUrlsForIndexTask(EdgeDomain domain, int domainId, int pass, int limit, double rank) {
        try (var connection = dataSource.getConnection()) {

            EdgeIndexTask indexTask = new EdgeIndexTask(domain, pass, limit, rank);

            try (var stmt =
                         connection.prepareStatement("select SQL_BUFFER_RESULT proto,url,port,visited from EC_URL USE INDEX (EC_URL_DOMAIN_ID) WHERE DOMAIN_ID=?")) {
                stmt.setFetchSize(limit);
                stmt.setInt(1, domainId);
                var rsp = stmt.executeQuery();
                while (rsp.next()) {
                    var url = new EdgeUrl(rsp.getString(1),
                            domain,
                            rsp.getInt(3),
                            rsp.getString(2)
                    );

                    if (rsp.getBoolean(4)) {
                        indexTask.visited.add(url.hashCode());
                    } else if (indexTask.urls.size() < limit) {
                        indexTask.urls.add(url);
                    }
                }
            } catch (Exception ex) {
                logger.error("DB error", ex);
            }
            return indexTask;
        }
    }


}

@AllArgsConstructor
class EdgeDomainDiscoverTask {
    public final EdgeDomain domain;
    public final int id;
    public final double rank;

    @Override
    public int hashCode() {
        return domain.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (other instanceof EdgeDomainDiscoverTask) {
            EdgeDomainDiscoverTask o = (EdgeDomainDiscoverTask)other;
            return id == o.id;
        }
        return false;
    }

}
