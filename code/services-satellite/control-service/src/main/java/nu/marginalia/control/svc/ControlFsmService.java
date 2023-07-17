package nu.marginalia.control.svc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.control.fsm.ControlFSMs;
import nu.marginalia.control.fsm.task.ReconvertAndLoadFSM;
import nu.marginalia.control.model.ControlProcess;
import nu.marginalia.control.model.ControlProcessState;
import nu.marginalia.db.storage.model.FileStorageId;
import nu.marginalia.mqsm.state.MachineState;
import spark.Request;
import spark.Response;

import java.util.List;
import java.util.Map;

@Singleton
public class ControlFsmService {
    private final ControlFSMs controlFSMs;

    @Inject
    public ControlFsmService(ControlFSMs controlFSMs) {
        this.controlFSMs = controlFSMs;
    }

    public Object startFsm(Request req, Response rsp) throws Exception {
        controlFSMs.start(
                ControlProcess.valueOf(req.params("fsm").toUpperCase())
        );
        return "";
    }

    public Object stopFsm(Request req, Response rsp) throws Exception {
        controlFSMs.stop(
                ControlProcess.valueOf(req.params("fsm").toUpperCase())
        );
        return "";
    }

    public Object triggerProcessing(Request request, Response response) throws Exception {
        controlFSMs.start(
                ControlProcess.RECONVERT_LOAD,
                FileStorageId.of(Integer.parseInt(request.params("fid")))
        );
        return "";
    }

    public Object loadProcessedData(Request request, Response response) throws Exception {
        var fid = FileStorageId.of(Integer.parseInt(request.params("fid")));

        // Start the FSM from the intermediate state that triggers the load
        controlFSMs.startFrom(
                ControlProcess.RECONVERT_LOAD,
                ReconvertAndLoadFSM.LOAD,
                new ReconvertAndLoadFSM.Message(null, fid, 0L, 0L)
        );

        return "";
    }

    public Object getFsmStates() {
        return controlFSMs.getMachineStates().entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e -> {

            final MachineState state = e.getValue();
            final String machineName = e.getKey().name();
            final String stateName = state.name();
            final boolean terminal = state.isFinal();

            return new ControlProcessState(machineName, stateName, terminal);
        }).toList();
    }
}
