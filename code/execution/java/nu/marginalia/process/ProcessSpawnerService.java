package nu.marginalia.process;

import nu.marginalia.converting.ConverterMain;
import nu.marginalia.crawl.CrawlerMain;
import nu.marginalia.livecrawler.LiveCrawlerMain;
import nu.marginalia.loading.LoaderMain;
import nu.marginalia.ndp.NdpMain;
import nu.marginalia.ping.PingMain;
import nu.marginalia.task.ExportTasksMain;

import java.util.Arrays;
import java.util.List;

public interface ProcessSpawnerService {

    static ProcessId translateExternalIdBase(String id) {
        for (var processId : ProcessId.values()) {
            if (processId.processName.equals(id))
                return processId;
        }
        return null;
    }

    enum ProcessId {
        CRAWLER(CrawlerMain.class, "crawler"),
        PING(PingMain.class, "ping"),
        LIVE_CRAWLER(LiveCrawlerMain.class, "live-crawler"),
        CONVERTER(ConverterMain.class, "converter"),
        LOADER(LoaderMain.class, "loader"),
        INDEX_CONSTRUCTOR("nu.marginalia.index.IndexConstructorMain", "index-constructor"),
        RANKING_CONSTRUCTOR("nu.marginalia.index.RankingConstructorMain", "ranking-constructor"),
        NDP(NdpMain.class, "ndp"),
        EXPORT_TASKS(ExportTasksMain.class, "export-tasks"),
        ;

        public final String mainClass;
        public final String processName;

        ProcessId(Class<? extends ProcessMainClass> mainClass,
                  String processName
                  ) {
            this.mainClass = mainClass.getName();
            this.processName = processName;
        }
        ProcessId(String mainClassFullName, String processName) {
            this.mainClass = mainClassFullName;
            this.processName = processName;
        }

        List<String> envOpts() {
            String variable = switch (this) {
                case CRAWLER -> "CRAWLER_PROCESS_OPTS";
                case LIVE_CRAWLER -> "LIVE_CRAWLER_PROCESS_OPTS";
                case CONVERTER -> "CONVERTER_PROCESS_OPTS";
                case LOADER -> "LOADER_PROCESS_OPTS";
                case PING -> "PING_PROCESS_OPTS";
                case NDP -> "NDP_PROCESS_OPTS";
                case INDEX_CONSTRUCTOR -> "INDEX_CONSTRUCTION_PROCESS_OPTS";
                case RANKING_CONSTRUCTOR -> "RANKING_CONSTRUCTION_PROCESS_OPTS";
                case EXPORT_TASKS -> "EXPORT_TASKS_PROCESS_OPTS";
            };
            String value = System.getenv(variable);

            if (value == null)
                return List.of();
            else
                return Arrays.asList(value.split("\\s+"));
        }
    }

    /** Starts the process and blocks until it terminates, returning true if it exited
     * successfully.  Returns false without starting anything if the process is already
     * running on this node. */
    boolean trigger(ProcessId processId, String... extraArgs) throws Exception;

    boolean isRunning(ProcessId processId);

    /** Requests termination of the process.  Returns false if it was not running. */
    boolean kill(ProcessId processId);
}
