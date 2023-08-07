package nu.marginalia.mqsm.graph;

/** Exception thrown by a state to indicate that the state machine should jump to a different state. */
public class ControlFlowException extends RuntimeException {
    private final String state;
    private final Object payload;

    public ControlFlowException(String state, Object payload) {
        this.state = state;
        this.payload = payload;
    }

    public String getState() {
        return state;
    }

    public Object getPayload() {
        return payload;
    }

    public StackTraceElement[] getStackTrace() { return new StackTraceElement[0]; }
}
