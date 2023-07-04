package nu.marginalia.mqsm.state;

public class FinalState implements MachineState {
    @Override
    public String name() { return "END"; }

    @Override
    public StateTransition next(String message) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isFinal() { return true; }
}
