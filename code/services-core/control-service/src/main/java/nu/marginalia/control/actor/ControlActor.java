package nu.marginalia.control.actor;

public enum ControlActor {

    MONITOR_MESSAGE_QUEUE,
    REBALANCE;

    public String id() {
        return "fsm:" + name().toLowerCase();
    }
}
