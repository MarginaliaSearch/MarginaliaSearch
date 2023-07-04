package nu.marginalia.mqsm.state;

public interface MachineState {
    String name();
    StateTransition next(String message);

    boolean isFinal();
}
