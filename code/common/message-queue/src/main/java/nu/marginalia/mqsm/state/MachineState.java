package nu.marginalia.mqsm.state;

import nu.marginalia.mqsm.graph.ResumeBehavior;

public interface MachineState {
    String name();

    StateTransition next(String message);

    ResumeBehavior resumeBehavior();

    boolean isFinal();

}
