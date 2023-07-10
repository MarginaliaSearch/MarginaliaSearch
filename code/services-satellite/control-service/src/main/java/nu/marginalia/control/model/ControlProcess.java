package nu.marginalia.control.model;

public enum ControlProcess {
    REPARTITION_REINDEX;

    public String id() {
        return "fsm:" + name().toLowerCase();
    }
}
