package nu.marginalia.control.svc;

import com.google.inject.name.Named;
import nu.marginalia.service.control.ServiceEventLog;
import nu.marginalia.service.server.BaseServiceParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.utils.IOUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class ProcessService {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ServiceEventLog eventLog;
    private final Path distPath;

    private final ConcurrentHashMap<ProcessId, Process> processes = new ConcurrentHashMap<>();

    public enum ProcessId {
        CRAWLER("crawler-process/bin/crawler-process"),
        CONVERTER("converter-process/bin/converter-process"),
        LOADER("loader-process/bin/loader-process");

        public final String path;
        ProcessId(String path) {
            this.path = path;
        }
    };

    @Inject
    public ProcessService(BaseServiceParams params,
                          @Named("distPath") Path distPath) {
        this.eventLog = params.eventLog;
        this.distPath = distPath;
    }

    public boolean trigger(ProcessId processId) throws Exception {
        String processPath = processPath(processId);
        String[] args = new String[] {
                processPath
        };
        String[] env = env();

        Process process;

        if (!Files.exists(Path.of(processPath))) {
            logger.error("Process not found: {}", processPath);
            return false;
        }


        logger.info("Starting process: {}", processId + ": " + Arrays.toString(args) + " // " + Arrays.toString(env));
        synchronized (processes) {
            if (processes.containsKey(processId)) return false;
            process = Runtime.getRuntime().exec(args, env);
            processes.put(processId, process);
        }

        try (var es = new BufferedReader(new InputStreamReader(process.getErrorStream()));
             var os = new BufferedReader(new InputStreamReader(process.getInputStream()))
        ) {
            eventLog.logEvent("PROCESS-STARTED", processId.toString());
            process.onExit().whenComplete((p,t) -> eventLog.logEvent("PROCESS-EXIT", processId.toString()));

            while (process.isAlive()) {
                if (es.ready())
                    logger.warn("{}:{}", processId, es.readLine());
                if (os.ready())
                    logger.debug("{}:{}", processId, os.readLine());
            }

            return 0 == process.waitFor();
        }
        finally {
            processes.remove(processId);
        }


    }

    public boolean isRunning(ProcessId processId) {
        return processes.containsKey(processId);
    }

    public boolean kill(ProcessId processId) {
        Process process = processes.get(processId);
        if (process == null) return false;

        eventLog.logEvent("PROCESS-KILL", processId.toString());
        process.destroy();
        processes.remove(processId);

        return true;
    }

    private String processPath(ProcessId id) {
        return distPath.resolve(id.path).toString();
    }

    private String[] env() {

        Map<String, String> opts = new HashMap<>();
        String WMSA_HOME = System.getenv("WMSA_HOME");
        if (WMSA_HOME == null || WMSA_HOME.isBlank()) {
            WMSA_HOME = "/var/lib/wmsa";
        }
        opts.put("WMSA_HOME", WMSA_HOME);
        opts.put("JAVA_HOME", System.getenv("JAVA_HOME"));

        return opts.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toArray(String[]::new);
    }
}
