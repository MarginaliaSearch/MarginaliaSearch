package nu.marginalia.loading;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.zaxxer.hikari.HikariDataSource;
import lombok.SneakyThrows;
import nu.marginalia.work_log.WorkLog;
import nu.marginalia.crawling.common.plan.CrawlPlanLoader;
import nu.marginalia.crawling.common.plan.CrawlPlan;
import nu.marginalia.loading.loader.IndexLoadKeywords;
import nu.marginalia.loading.loader.Loader;
import nu.marginalia.loading.loader.LoaderFactory;
import nu.marginalia.converting.instruction.Instruction;
import nu.marginalia.service.module.DatabaseModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class LoaderMain {
    private static final Logger logger = LoggerFactory.getLogger(LoaderMain.class);

    private final CrawlPlan plan;
    private final ConvertedDomainReader instructionsReader;
    private final LoaderFactory loaderFactory;

    private final IndexLoadKeywords indexLoadKeywords;
    private volatile boolean running = true;

    final Thread processorThread = new Thread(this::processor, "Processor Thread");

    public static void main(String... args) throws IOException {
        if (args.length != 1) {
            System.err.println("Arguments: crawl-plan.yaml");
            System.exit(0);
        }

        new org.mariadb.jdbc.Driver();

        var plan = new CrawlPlanLoader().load(Path.of(args[0]));

        Injector injector = Guice.createInjector(
                new LoaderModule(plan),
                new DatabaseModule()
        );

        var instance = injector.getInstance(LoaderMain.class);
        instance.run();
    }

    @Inject
    public LoaderMain(CrawlPlan plan,
                      ConvertedDomainReader instructionsReader,
                      HikariDataSource dataSource,
                      LoaderFactory loaderFactory, IndexLoadKeywords indexLoadKeywords) {

        this.plan = plan;
        this.instructionsReader = instructionsReader;
        this.loaderFactory = loaderFactory;
        this.indexLoadKeywords = indexLoadKeywords;

        nukeTables(dataSource);

        Runtime.getRuntime().addShutdownHook(new Thread(this::shutDownIndex));
        processorThread.start();
    }

    private void nukeTables(HikariDataSource dataSource) {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("SET FOREIGN_KEY_CHECKS = 0");
            stmt.execute("TRUNCATE TABLE EC_PAGE_DATA");
            stmt.execute("TRUNCATE TABLE EC_URL");
            stmt.execute("TRUNCATE TABLE EC_DOMAIN_LINK");
            stmt.execute("SET FOREIGN_KEY_CHECKS = 1");
        }
        catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    @SneakyThrows
    private void shutDownIndex() {
        // This must run otherwise the journal doesn't get a proper header
        indexLoadKeywords.close();
    }

    @SneakyThrows
    public void run() {
        var logFile = plan.process.getLogFile();

        AtomicInteger loadTotal = new AtomicInteger();
        WorkLog.readLog(logFile, entry -> { loadTotal.incrementAndGet(); });
        LoaderMain.loadTotal = loadTotal.get();

        WorkLog.readLog(logFile, entry -> {
            load(plan, entry.path(), entry.cnt());
        });

        running = false;
        processorThread.join();

        System.exit(0);
    }

    private volatile static int loadTotal;

    private void load(CrawlPlan plan, String path, int cnt) {
        Path destDir = plan.getProcessedFilePath(path);
        try {
            var loader = loaderFactory.create(cnt);
            var instructions = instructionsReader.read(destDir, cnt);
            processQueue.put(new LoadJob(path, loader, instructions));
        } catch (Exception e) {
            logger.error("Failed to load " + destDir, e);
        }
    }

    static final TaskStats taskStats = new TaskStats(100);

    private record LoadJob(String path, Loader loader, List<Instruction> instructionList) {
        public void run() {
            long startTime = System.currentTimeMillis();
            for (var i : instructionList) {
                try {
                    i.apply(loader);
                }
                catch (Exception ex) {
                    logger.error("Failed to load instruction {}", i);
                }
            }

            loader.finish();
            long loadTime = System.currentTimeMillis() - startTime;
            taskStats.observe(loadTime);
            logger.info("Loaded {}/{} : {} ({}) {}ms {} l/s", taskStats.getCount(),
                    loadTotal, path, loader.data.sizeHint, loadTime, taskStats.avgTime());
        }

    }

    private static final LinkedBlockingQueue<LoadJob> processQueue = new LinkedBlockingQueue<>(2);

    private void processor() {
        try {
            while (running || !processQueue.isEmpty()) {
                LoadJob job = processQueue.poll(1, TimeUnit.SECONDS);

                if (job != null) {
                    job.run();
                }
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

    }

}
