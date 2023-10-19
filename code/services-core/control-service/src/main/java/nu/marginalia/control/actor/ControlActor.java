package nu.marginalia.control.actor;

public enum ControlActor {
    REBALANCE;

    public String id() {
        return "fsm:" + name().toLowerCase();
    }
}
