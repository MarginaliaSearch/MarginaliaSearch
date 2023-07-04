package nu.marginalia.mqsm.state;

public record StateTransition(String state, String message) {
    public static StateTransition to(String state) {
        return new StateTransition(state, "");
    }

    public static StateTransition to(String state, String message) {
        return new StateTransition(state, message);
    }
}
