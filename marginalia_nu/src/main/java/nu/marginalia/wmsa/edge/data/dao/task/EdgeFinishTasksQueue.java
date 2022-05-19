package nu.marginalia.wmsa.edge.data.dao.task;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
import lombok.AllArgsConstructor;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.crawl.EdgeDomainIndexingState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.LinkedBlockingQueue;

@Singleton
public class EdgeFinishTasksQueue {
    private final HikariDataSource dataSource;
    private final EdgeDataStoreTaskOngoingJobs ongoingJobs;
    private final LinkedBlockingQueue<EdgeFinishTaskSpecs> finishTasksQueue = new LinkedBlockingQueue<>(10);
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public EdgeFinishTasksQueue(HikariDataSource dataSource, EdgeDataStoreTaskOngoingJobs ongoingJobs) {
        this.dataSource = dataSource;
        this.ongoingJobs = ongoingJobs;

        var finishIndexTasks = new Thread(this::finishIndexTasks, "FinishIndexTasks");
        finishIndexTasks.setDaemon(true);
        finishIndexTasks.start();
    }


    private void finishIndexTasks() {
        for (;;) {
            try (var connection = dataSource.getConnection()) {

                var task = finishTasksQueue.take();

                connection.setAutoCommit(false);

                if (task.quality != null) {

                    try (var stmt =
                                 connection.prepareStatement("UPDATE EC_DOMAIN LEFT JOIN (SELECT DOMAIN_ID, AVG(QUALITY_MEASURE) AVGQ FROM EC_URL GROUP BY DOMAIN_ID) QUALITY ON EC_DOMAIN.ID = QUALITY.DOMAIN_ID SET INDEXED=INDEXED+1, INDEX_DATE=NOW(), QUALITY=IFNULL(AVGQ,?-INDEXED/2)*IFNULL(RANK,1), QUALITY_RAW=IFNULL(AVGQ,-5), STATE=? WHERE EC_DOMAIN.URL_PART=?")) {
                        stmt.setDouble(1, task.quality);
                        stmt.setInt(2, task.state.code);
                        stmt.setString(3, task.domain.toString());
                        stmt.execute();

                        connection.commit();
                        ongoingJobs.remove(task.domain);
                    } catch (Exception ex) {
                        logger.error("DB error", ex);
                        connection.rollback();
                    }
                }
                else {
                    try (var stmt =
                                 connection.prepareStatement("UPDATE EC_DOMAIN SET STATE=? WHERE URL_PART=?")) {
                        stmt.setInt(1, task.state.code);
                        stmt.setString(2, task.domain.toString());
                        stmt.execute();

                        connection.commit();
                        ongoingJobs.remove(task.domain);
                    } catch (Exception ex) {
                        logger.error("DB error", ex);
                        connection.rollback();
                    }
                }
            }
            catch (Exception ex) {
                logger.error("DB error", ex);
            }
        }
    }

    public void add(EdgeDomain domain, double quality, EdgeDomainIndexingState state) throws InterruptedException {
        finishTasksQueue.put(new EdgeFinishTaskSpecs(domain, quality, state));
    }
    public void addError(EdgeDomain domain, EdgeDomainIndexingState state) throws InterruptedException {
        finishTasksQueue.put(new EdgeFinishTaskSpecs(domain, null, state));
    }
    @AllArgsConstructor
    private static class EdgeFinishTaskSpecs {
        public final EdgeDomain domain;
        public final Double quality;
        public final EdgeDomainIndexingState state;
    }

}

