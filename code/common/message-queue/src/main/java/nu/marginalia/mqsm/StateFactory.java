package nu.marginalia.mqsm;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.mqsm.graph.ResumeBehavior;
import nu.marginalia.mqsm.state.MachineState;
import nu.marginalia.mqsm.state.StateTransition;

import java.util.function.Function;
import java.util.function.Supplier;

@Singleton
public class StateFactory {
    private final Gson gson;

    @Inject
    public StateFactory(Gson gson) {
        this.gson = gson;
    }

    public <T> MachineState create(String name, ResumeBehavior resumeBehavior, Class<T> param, Function<T, StateTransition> logic) {
        return new MachineState() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public StateTransition next(String message) {

                if (message.equals("")) {
                    return logic.apply(null);
                }

                return logic.apply(gson.fromJson(message, param));
            }

            @Override
            public ResumeBehavior resumeBehavior() {
                return resumeBehavior;
            }

            @Override
            public boolean isFinal() {
                return false;
            }
        };
    }

    public MachineState create(String name, ResumeBehavior resumeBehavior, Supplier<StateTransition> logic) {
        return new MachineState() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public StateTransition next(String message) {
                return logic.get();
            }

            @Override
            public ResumeBehavior resumeBehavior() {
                return resumeBehavior;
            }

            @Override
            public boolean isFinal() {
                return false;
            }
        };
    }

    public StateTransition transition(String state) {
        return StateTransition.to(state);
    }

    public StateTransition transition(String state, Object message) {

        if (null == message) {
            return StateTransition.to(state);
        }

        return StateTransition.to(state, gson.toJson(message));
    }

    public static class ErrorState implements MachineState {
        @Override
        public String name() { return "ERROR"; }

        @Override
        public StateTransition next(String message) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResumeBehavior resumeBehavior() { return ResumeBehavior.RETRY; }

        @Override
        public boolean isFinal() { return true; }
    }

    public static class FinalState implements MachineState {
        @Override
        public String name() { return "END"; }

        @Override
        public StateTransition next(String message) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResumeBehavior resumeBehavior() { return ResumeBehavior.RETRY; }

        @Override
        public boolean isFinal() { return true; }
    }

    public static class ResumingState implements MachineState {
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
}
