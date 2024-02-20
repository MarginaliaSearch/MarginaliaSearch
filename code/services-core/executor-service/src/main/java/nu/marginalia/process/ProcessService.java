package nu.marginalia.process;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import nu.marginalia.service.control.ServiceEventLog;
import nu.marginalia.service.server.BaseServiceParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class ProcessService {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Marker processMarker = MarkerFactory.getMarker("PROCESS");

    private final ServiceEventLog eventLog;
    private final Path distPath;

    private final ConcurrentHashMap<ProcessId, Process> processes = new ConcurrentHashMap<>();


    public static ProcessService.ProcessId translateExternalIdBase(String id) {
        return switch (id) {
            case "converter" -> ProcessService.ProcessId.CONVERTER;
            case "crawler" -> ProcessService.ProcessId.CRAWLER;
            case "loader" -> ProcessService.ProcessId.LOADER;
            case "website-adjacencies-calculator" -> ProcessService.ProcessId.ADJACENCIES_CALCULATOR;
            case "index-constructor" -> ProcessService.ProcessId.INDEX_CONSTRUCTOR;
            default -> null;
        };
    }

    public enum ProcessId {
        CRAWLER("crawler-process/bin/crawler-process"),
        CONVERTER("converter-process/bin/converter-process"),
        LOADER("loader-process/bin/loader-process"),
        INDEX_CONSTRUCTOR("index-construction-process/bin/index-construction-process"),
        ADJACENCIES_CALCULATOR("website-adjacencies-calculator/bin/website-adjacencies-calculator")
        ;

        public final String path;
        ProcessId(String path) {
            this.path = path;
        }
    }

    @Inject
    public ProcessService(BaseServiceParams params,
                          @Named("distPath") Path distPath) {
        this.eventLog = params.eventLog;
        this.distPath = distPath;
    }

    public boolean trigger(ProcessId processId) throws Exception {
        return trigger(processId, new String[0]);
    }

    public boolean trigger(ProcessId processId, String... parameters) throws Exception {
        final String processPath = distPath.resolve(processId.path).toString();
        final String[] env = createEnvironmentVariables();
        final String[] args = createCommandArguments(processPath, parameters);

        Process process;

        if (!Files.exists(Path.of(processPath))) {
            logger.error("Process not found: {}", processPath);
            return false;
        }

        logger.info("Starting process: {}: {} // {}", processId, Arrays.toString(args), Arrays.toString(env));

        synchronized (processes) {
            if (processes.containsKey(processId)) return false;
            process = Runtime.getRuntime().exec(args, env);
            processes.put(processId, process);
        }

        eventLog.logEvent("PROCESS-START", processId.toString());
        try {
            new Thread(new ProcessLogStderr(process)).start();
            new Thread(new ProcessLogStdout(process)).start();

            final int returnCode = process.waitFor();
            logger.info("Process {} terminated with code {}", processId, returnCode);
            return 0 == returnCode;
        }
        catch (Exception ex) {
            logger.info("Process {} terminated with exception", processId);
            throw ex;
        }
        finally {
            eventLog.logEvent("PROCESS-EXIT", processId.toString());
            processes.remove(processId);
        }
    }


    private String[] createCommandArguments(String processPath, String[] parameters) {
        final String[] args = new String[parameters.length + 1];
        args[0] = processPath;
        System.arraycopy(parameters, 0, args, 1, parameters.length);
        return args;
    }

    public boolean isRunning(ProcessId processId) {
        return processes.containsKey(processId);
    }

    public boolean kill(ProcessId processId) {
        Process process = processes.get(processId);
        if (process == null) return false;

        eventLog.logEvent("PROCESS-KILL", processId.toString());
        process.destroy();

        return true;
    }

    /** These environment variables are propagated from the parent process to the child process,
     * along with WMSA_HOME, but it has special logic */
    private final List<String> propagatedEnvironmentVariables = List.of(
            "JAVA_HOME",
            "WMSA_SERVICE_NODE",
            "CONVERTER_PROCESS_OPTS",
            "LOADER_PROCESS_OPTS",
            "INDEX_CONSTRUCTION_PROCESS_OPTS",
            "CRAWLER_PROCESS_OPTS");

    private String[] createEnvironmentVariables() {
        List<String> opts = new ArrayList<>();

        String WMSA_HOME = System.getenv("WMSA_HOME");

        if (WMSA_HOME == null || WMSA_HOME.isBlank()) {
            WMSA_HOME = "/var/lib/wmsa";
        }

        opts.add(env2str("WMSA_HOME", WMSA_HOME));
        opts.add(env2str("JAVA_OPTS", "--enable-preview")); //

        for (String envKey : propagatedEnvironmentVariables) {
            String envValue = System.getenv(envKey);
            if (envValue != null && !envValue.isBlank()) {
                opts.add(env2str(envKey, envValue));
            }
        }

        return opts.toArray(String[]::new);
    }

    private String env2str(String key, String val) {
        return key + "=" + val;
    }




    class ProcessLogStderr implements Runnable {
        private final Process process;

        public ProcessLogStderr(Process process) {
            this.process = process;
        }

        @Override
        public void run() {
            try (var es = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while (((line = es.readLine()) != null)) {
                    logger.warn(processMarker, line);
                }
            }
            catch (IOException ex) {
                logger.error("Error reading process error stream", ex);
            }
        }
    }

    class ProcessLogStdout implements Runnable {
        private final Process process;

        public ProcessLogStdout(Process process) {
            this.process = process;
        }

        @Override
        public void run() {
            try (var is = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while (((line = is.readLine()) != null)) {
                    logger.info(processMarker, line);
                }
            }
            catch (IOException ex) {
                logger.error("Error reading process output stream", ex);
            }
        }
    }

}
