package nu.marginalia.wmsa.edge.converting;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.zaxxer.hikari.HikariDataSource;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.configuration.module.DatabaseModule;
import nu.marginalia.wmsa.edge.converting.interpreter.Instruction;
import nu.marginalia.wmsa.edge.converting.loader.Loader;
import nu.marginalia.wmsa.edge.converting.loader.LoaderFactory;
import nu.marginalia.wmsa.edge.crawling.CrawlPlanLoader;
import nu.marginalia.wmsa.edge.crawling.WorkLog;
import nu.marginalia.wmsa.edge.index.client.EdgeIndexClient;
import nu.marginalia.wmsa.edge.model.EdgeCrawlPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class LoaderMain {

    private final Path processDir;
    private final EdgeCrawlPlan plan;
    private final ConvertedDomainReader instructionsReader;
    private final HikariDataSource dataSource;

    private static final Logger logger = LoggerFactory.getLogger(LoaderMain.class);
    private final LoaderFactory loaderFactory;
    private final EdgeIndexClient indexClient;
    private final boolean running = true;

    final Thread processorThread = new Thread(this::processor, "Processor Thread");

    public static void main(String... args) throws IOException {
        if (args.length != 1) {
            System.err.println("Arguments: crawl-plan.yaml");
            System.exit(0);
        }
        var plan = new CrawlPlanLoader().load(Path.of(args[0]));

        Injector injector = Guice.createInjector(
                new ConverterModule(plan),
                new DatabaseModule()
        );

        var instance = injector.getInstance(LoaderMain.class);
        instance.run();
    }

    @Inject
    public LoaderMain(EdgeCrawlPlan plan,
                      ConvertedDomainReader instructionsReader,
                      HikariDataSource dataSource,
                      LoaderFactory loaderFactory,
                      EdgeIndexClient indexClient) {

        this.processDir = plan.process.getDir();
        this.plan = plan;
        this.instructionsReader = instructionsReader;
        this.dataSource = dataSource;
        this.loaderFactory = loaderFactory;
        this.indexClient = indexClient;

        processorThread.start();
    }

    @SneakyThrows
    public void run() {
        var logFile = plan.process.getLogFile();

        AtomicInteger loadTotal = new AtomicInteger();
        WorkLog.readLog(logFile, entry -> { loadTotal.incrementAndGet(); });
        LoaderMain.loadTotal = loadTotal.get();

        WorkLog.readLog(logFile, entry -> {
            load(entry.path(), entry.cnt());
        });

        processorThread.join();
        indexClient.close();
    }

    private volatile static int loadTotal;
    private static final int loaded = 0;

    private void load(String path, int cnt) {
        String first = path.substring(0, 2);
        String second = path.substring(2, 4);
        Path destDir = processDir.resolve(first).resolve(second).resolve(path);



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
                i.apply(loader);
            }

            loader.finish();
            long loadTime = System.currentTimeMillis() - startTime;
            taskStats.observe(loadTime);
            logger.info("Loaded {}/{} : {} ({}) {}ms {} l/s", taskStats.getCount(), loadTotal, path, loader.data.sizeHint, loadTime, taskStats.avgTime());
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
