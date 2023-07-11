package nu.marginalia.control.model;

public enum ControlProcess {
    REPARTITION_REINDEX,
    RECONVERT_LOAD;

    public String id() {
        return "fsm:" + name().toLowerCase();
    }
}
