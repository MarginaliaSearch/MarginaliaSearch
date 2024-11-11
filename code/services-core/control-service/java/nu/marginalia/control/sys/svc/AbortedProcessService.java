package nu.marginalia.control.sys.svc;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.control.ControlRendererFactory;
import nu.marginalia.control.RedirectControl;
import nu.marginalia.control.sys.model.AbortedProcess;
import nu.marginalia.model.gson.GsonFactory;
import nu.marginalia.mq.MqMessageState;
import nu.marginalia.mq.persistence.MqPersistence;
import nu.marginalia.nodecfg.NodeConfigurationService;
import nu.marginalia.nodecfg.model.NodeConfiguration;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorage;
import nu.marginalia.storage.model.FileStorageId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Control for listing and restarting aborted processes.
 * */
public class AbortedProcessService {
    private static final Logger logger = LoggerFactory.getLogger(AbortedProcessService.class);
    private static final Gson gson = GsonFactory.get();
    private final HikariDataSource dataSource;
    private final FileStorageService fileStorageService;
    private final ControlRendererFactory rendererFactory;
    private final RedirectControl redirectControl;
    private final MqPersistence mqPersistence;
    private final NodeConfigurationService nodeConfigurationService;

    @Inject
    public AbortedProcessService(HikariDataSource dataSource,
                                 FileStorageService fileStorageService,
                                 ControlRendererFactory rendererFactory,
                                 RedirectControl redirectControl,
                                 MqPersistence mqPersistence,
                                 NodeConfigurationService nodeConfigurationService)
    {
        this.dataSource = dataSource;
        this.fileStorageService = fileStorageService;
        this.rendererFactory = rendererFactory;
        this.redirectControl = redirectControl;
        this.mqPersistence = mqPersistence;
        this.nodeConfigurationService = nodeConfigurationService;
    }

    public void register() {
        var abortedProcessesRenderer = rendererFactory.renderer("control/sys/aborted-processes");

        Spark.get("/aborted-processes", this::abortedProcessesModel, abortedProcessesRenderer::render);
        Spark.get("/aborted-processes/", this::abortedProcessesModel, abortedProcessesRenderer::render);
        Spark.post("/aborted-processes/:id", this::restartProcess, redirectControl.renderRedirectAcknowledgement("Restarting...", "/"));
    }

    private Object abortedProcessesModel(Request request, Response response) {
        return Map.of("abortedProcesses", getAbortedProcesses());
    }

    private Object restartProcess(Request request, Response response) throws SQLException {
        long msgId = Long.parseLong(request.params("id"));
        mqPersistence.updateMessageState(msgId, MqMessageState.NEW);
        return "";
    }


    private List<AbortedProcess> getAbortedProcesses() {
        List<Integer> allNodeIds = nodeConfigurationService.getAll().stream()
                .map(NodeConfiguration::getId)
                .toList();

        // Generate all possible values for process-related inboxes
        String inboxes = Stream.of("converter", "loader", "crawler")
                .flatMap(s -> allNodeIds.stream().map(i -> "'" + s + ":" + i + "'"))
                .collect(Collectors.joining(",", "(", ")"));

        try (var conn = dataSource.getConnection()) {
            var stmt = conn.prepareStatement("SELECT ID, RECIPIENT_INBOX, CREATED_TIME, UPDATED_TIME, PAYLOAD FROM MESSAGE_QUEUE\nWHERE STATE = 'DEAD'\nAND RECIPIENT_INBOX IN " + inboxes + "\n"); // SQL injection safe, string is not user input
            var rs = stmt.executeQuery();

            List<AbortedProcess> abortedProcesses = new ArrayList<>();
            while (rs.next()) {
                var msgId = rs.getLong("ID");
                var recipientInbox = rs.getString("RECIPIENT_INBOX");
                var createdTime = rs.getString("CREATED_TIME");
                var updatedTime = rs.getString("UPDATED_TIME");
                var payload = rs.getString("PAYLOAD");

                List<FileStorageId> associatedStorageIds = getAssociatedStoragesIds(payload);
                List<FileStorage> associatedStorages = fileStorageService.getStorage(associatedStorageIds);

                abortedProcesses.add(new AbortedProcess(recipientInbox, msgId, createdTime, updatedTime, associatedStorages));
            }

            abortedProcesses.sort(Comparator.comparing(AbortedProcess::stopDateTime).reversed());

            return abortedProcesses;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /* Attempt to parse the JSON payload and extract the file storage ids
    * from the data, not knowing exactly what the payload looks like.
    * */
    private List<FileStorageId> getAssociatedStoragesIds(String payload) {
        Map<?,?> fields = gson.fromJson(payload, LinkedTreeMap.class);
        logger.info("{}", fields);
        List<FileStorageId> associatedStorageIds = new ArrayList<>();

        // We expect a map of objects, where some objects are a map with an "id" field
        // and an integer value.  We want to extract the integer values.
        for (Object field : fields.values()) {
            if ((field instanceof Map<?,?> m) && (m.get("id") instanceof Number i))
                associatedStorageIds.add(FileStorageId.of(i.intValue()));

            if (field instanceof List) {
                for (Object o : (List<?>) field) {
                    if ((o instanceof Map<?,?> m) && (m.get("id") instanceof Number i))
                        associatedStorageIds.add(FileStorageId.of(i.intValue()));
                }
            }
        }

        return associatedStorageIds;
    }

}
