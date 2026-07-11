package nu.marginalia.process;

import nu.marginalia.WmsaHome;
import nu.marginalia.service.control.ServiceEventLog;
import nu.marginalia.service.server.BaseServiceParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractProcessSpawnerService implements ProcessSpawnerService {
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    private final Marker processMarker = MarkerFactory.getMarker("PROCESS");

    private final ServiceEventLog eventLog;

    private final ConcurrentHashMap<ProcessId, Process> processes = new ConcurrentHashMap<>();
    protected final int node;

    protected AbstractProcessSpawnerService(BaseServiceParams params) {
        this.eventLog = params.eventLog;
        this.node = params.configuration.node();
    }

    /** Embeds the java invocation in the launch mechanism.  The default launches it
     * as-is, directly as a child process. */
    protected List<String> wrapCommand(ProcessId processId, List<String> javaCommand) throws Exception {
        return javaCommand;
    }

    /** Terminates a process previously launched by wrapCommand.  The default destroys
     * the child process. */
    protected void destroyProcess(ProcessId processId, Process process) {
        process.destroy();
    }

    /** Judges the outcome of a finished process from the watcher's exit code.  The
     * default takes the exit code at face value, appropriate when the watcher is the
     * process itself. */
    protected boolean interpretResult(ProcessId processId, int exitCode) {
        return exitCode == 0;
    }

    public boolean trigger(ProcessId processId, String... extraArgs) throws Exception {
        final String[] env = createEnvironmentVariables();
        final List<String> args = wrapCommand(processId, createJavaCommand(processId, extraArgs));

        Process process;

        logger.info("Starting process: {} {}", processId, processId.envOpts());

        synchronized (processes) {
            if (processes.containsKey(processId)) return false;
            process = Runtime.getRuntime().exec(args.toArray(String[]::new), env);
            processes.put(processId, process);
        }

        eventLog.logEvent("PROCESS-START", processId.toString());
        try {
            new Thread(new ProcessLogStderr(process)).start();
            new Thread(new ProcessLogStdout(process)).start();

            final int returnCode = process.waitFor();
            logger.info("Process {} terminated with code {}", processId, returnCode);
            return interpretResult(processId, returnCode);
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

    private List<String> createJavaCommand(ProcessId processId, String... extraArgs) {
        List<String> args = new ArrayList<>();
        String javaHome = System.getProperty("java.home");

        args.add(javaHome + "/bin/java");
        args.add("-cp");
        args.add(System.getProperty("java.class.path"));

        if (getClass().desiredAssertionStatus()) args.add("-ea");
        else args.add("-da");

        args.add("--enable-preview");
        args.add("--enable-native-access=ALL-UNNAMED");

        args.add("-Dservice-name=" + processId.processName);

        String loggingOpts = System.getProperty("log4j2.configurationFile");
        if (loggingOpts != null) {
            args.add("-Dlog4j.configurationFile=" + loggingOpts);
        }

        if (System.getProperty("system.serviceNode") != null) {
            args.add("-Dsystem.serviceNode=" + System.getProperty("system.serviceNode"));
        }

        // Add SOCKS proxy properties for crawler processes
        if (System.getProperty("crawler.socksProxy.enabled") != null) {
            args.add("-Dcrawler.socksProxy.enabled=" + System.getProperty("crawler.socksProxy.enabled"));
        }
        if (System.getProperty("crawler.socksProxy.list") != null) {
            args.add("-Dcrawler.socksProxy.list=" + System.getProperty("crawler.socksProxy.list"));
        }
        if (System.getProperty("crawler.socksProxy.strategy") != null) {
            args.add("-Dcrawler.socksProxy.strategy=" + System.getProperty("crawler.socksProxy.strategy"));
        }

        if (Boolean.getBoolean("system.profile")) {
            // add jfr options
            args.add("-XX:+FlightRecorder");
            String jfrFileName = "/var/log/wmsa/profile-%s-%d-%s.jfr".formatted(
                    processId.toString(),
                    node,
                    LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME).replace(':', '.')
            );
            args.add("-XX:StartFlightRecording=filename=%s,name=%s".formatted(jfrFileName, processId.toString()));
        }

        args.addAll(processId.envOpts());
        args.add(processId.mainClass);
        args.addAll(Arrays.asList(extraArgs));

        return args;
    }

    public boolean isRunning(ProcessId processId) {
        return processes.containsKey(processId);
    }

    public boolean kill(ProcessId processId) {
        Process process = processes.get(processId);
        if (process == null) return false;

        eventLog.logEvent("PROCESS-KILL", processId.toString());
        destroyProcess(processId, process);

        return true;
    }

    /** These environment variables are propagated from the parent process to the child process,
     * along with WMSA_HOME, but it has special logic */
    private final List<String> propagatedEnvironmentVariables = List.of(
            "ZOOKEEPER_HOSTS",
            "WMSA_SERVICE_NODE"
    );

    protected List<String> createEnvironmentVariablesList() {
        List<String> opts = new ArrayList<>();

        opts.add(env2str("WMSA_HOME", WmsaHome.getHomePath().toString()));

        for (String envKey : propagatedEnvironmentVariables) {
            String envValue = System.getenv(envKey);
            if (envValue != null && !envValue.isBlank()) {
                opts.add(env2str(envKey, envValue));
            }
        }

        return opts;
    }

    private String[] createEnvironmentVariables() {
        return createEnvironmentVariablesList().toArray(String[]::new);
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
