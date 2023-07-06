package nu.marginalia.mqsm.state;

public class ResumingState implements MachineState {
    @Override
    public String name() { return "RESUMING"; }

    @Override
    public StateTransition next(String message) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResumeBehavior resumeBehavior() { return ResumeBehavior.RETRY; }

    @Override
    public boolean isFinal() { return false; }
}
