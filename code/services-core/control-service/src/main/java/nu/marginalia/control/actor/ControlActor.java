package nu.marginalia.control.actor;

public enum ControlActor {

    MONITOR_MESSAGE_QUEUE,
    REINDEX_ALL,
    REPROCESS_ALL,
    RECRAWL_ALL,
    REBALANCE;

    public String id() {
        return "fsm:" + name().toLowerCase();
    }
}
