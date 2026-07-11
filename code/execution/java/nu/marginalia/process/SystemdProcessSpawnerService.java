package nu.marginalia.process;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.service.server.BaseServiceParams;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Spawns the batch processes as transient systemd units via systemd-run.
 * <p>
 * The service user needs authorization to manage the transient units, which is
 * granted with a polkit rule scoped to the marginalia-proc- unit name prefix.
 */
@Singleton
public class SystemdProcessSpawnerService extends AbstractProcessSpawnerService {

    private static final String UNIT_PREFIX = "marginalia_proc-";

    /** Sandbox properties replayed from the service's unit onto the transient units.
     * Resource constraints such CPUAffinity and NUMAPolicy are deliberately excluded. */
    private static final List<String> SANDBOX_PROPERTIES = List.of(
            "User",
            "Group",
            "Slice",
            "PrivateMounts",
            "BindPaths",
            "BindReadOnlyPaths",
            "NetworkNamespacePath",
            "LimitNOFILE"
    );

    private volatile Map<String, String> sandboxProperties = null;

    @Inject
    public SystemdProcessSpawnerService(BaseServiceParams params) {
        super(params);
    }

    @Override
    protected List<String> wrapCommand(ProcessId processId, List<String> javaCommand) throws Exception {
        // Recover a running process
        if (isUnitActive(processId)) {
            logger.info("Adopting already-running unit {}", unitName(processId));
            return List.of("/usr/bin/systemctl", "start", "--wait", unitName(processId));
        }

        // A unit that failed lingers in failed state as the outcome signal (see
        // interpretResult()), clear any such leftover so the name is free
        resetFailedUnit(processId);

        List<String> command = new ArrayList<>();

        command.add("/usr/bin/systemd-run");
        command.add("--unit=" + unitName(processId));
        command.add("--description=Marginalia " + processId.processName + " process, node " + node);
        command.add("--wait");
        command.add("--quiet");
        command.add("--property=PartOf=marginalia.target");

        // The JVM exits 143 on SIGTERM
        command.add("--property=SuccessExitStatus=143");
        String nsPath = sandboxProperties().get("NetworkNamespacePath");
        if (nsPath != null && !nsPath.isBlank()) {
            String nsUnit = "marginalia_netns@" + nsPath.substring(nsPath.lastIndexOf('/') + 1) + ".service";
            command.add("--property=BindsTo=" + nsUnit);
            command.add("--property=After=" + nsUnit);
        }

        for (Map.Entry<String, String> property : sandboxProperties().entrySet()) {
            command.add("--property=" + property.getKey() + "=" + property.getValue());
        }

        for (String env : createEnvironmentVariablesList()) {
            command.add("--setenv=" + env);
        }

        command.add("--");
        command.addAll(javaCommand);

        return command;
    }

    @Override
    public boolean isRunning(ProcessId processId) {
        // The in-memory bookkeeping does not survive service restarts, but the unit
        // does thanks to a deterministic name
        return super.isRunning(processId) || isUnitActive(processId);
    }

    @Override
    protected boolean interpretResult(ProcessId processId, int exitCode) {
        // The adoption path's watcher cannot propagate the process exit status.
        //
        // The unit's failure state is the outcome signal instead.  A tarnsient unit
        // that fails lingers in failed state, a successful one unloads when it exits
        boolean failed = isUnitFailed(processId);
        if (failed) {
            resetFailedUnit(processId);
        }
        return exitCode == 0 && !failed;
    }

    @Override
    protected void destroyProcess(ProcessId processId, Process process) {
        try {
            int returnCode = systemctl("stop", unitName(processId));
            // Exit code 5 means the unit was not loaded, i.e. nothing left to stop
            if (returnCode != 0 && returnCode != 5) {
                logger.error("systemctl stop {} failed with code {}", unitName(processId), returnCode);
            }
        }
        catch (Exception ex) {
            logger.error("Failed to stop unit " + unitName(processId), ex);
        }
    }

    private boolean isUnitActive(ProcessId processId) {
        try {
            return systemctl("is-active", "--quiet", unitName(processId)) == 0;
        }
        catch (Exception ex) {
            logger.error("Failed to query unit " + unitName(processId), ex);
            return false;
        }
    }

    private boolean isUnitFailed(ProcessId processId) {
        try {
            return systemctl("is-failed", "--quiet", unitName(processId)) == 0;
        }
        catch (Exception ex) {
            logger.error("Failed to query unit " + unitName(processId), ex);
            return false;
        }
    }

    private void resetFailedUnit(ProcessId processId) {
        try {
            // Fails when there is nothing to reset, which is the common case
            systemctl("reset-failed", unitName(processId));
        }
        catch (Exception ex) {
            logger.error("Failed to reset unit " + unitName(processId), ex);
        }
    }

    private int systemctl(String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("/usr/bin/systemctl");
        command.addAll(List.of(args));
        return Runtime.getRuntime().exec(command.toArray(String[]::new)).waitFor();
    }

    private String unitName(ProcessId processId) {
        return UNIT_PREFIX + processId.processName + "-" + node + ".service";
    }

    private Map<String, String> sandboxProperties() throws IOException, InterruptedException {
        if (sandboxProperties == null) {
            sandboxProperties = querySandboxProperties(ownUnitName());
        }
        return sandboxProperties;
    }

    private Map<String, String> querySandboxProperties(String serviceUnit) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("/usr/bin/systemctl");
        command.add("show");
        command.add(serviceUnit);
        for (String property : SANDBOX_PROPERTIES) {
            command.add("-p");
            command.add(property);
        }

        Process show = Runtime.getRuntime().exec(command.toArray(String[]::new));

        Map<String, String> properties = new LinkedHashMap<>();
        try (var reader = new BufferedReader(new InputStreamReader(show.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int eq = line.indexOf('=');
                if (eq > 0 && eq < line.length() - 1) {
                    properties.put(line.substring(0, eq), line.substring(eq + 1));
                }
            }
        }

        int returnCode = show.waitFor();
        if (returnCode != 0) {
            throw new IOException("systemctl show " + serviceUnit + " failed with code " + returnCode);
        }

        logger.info("Transient unit sandbox from {}: {}", serviceUnit, properties);

        return properties;
    }

    /** The unit name of the service itself, from its cgroup path. */
    private String ownUnitName() throws IOException {
        for (String line : Files.readAllLines(Path.of("/proc/self/cgroup"))) {
            String unit = line.substring(line.lastIndexOf('/') + 1);
            if (unit.endsWith(".service")) {
                return unit;
            }
        }
        throw new IllegalStateException("??? Cannot detemrine own unit name from /proc/self/cgroup ???");
    }
}
