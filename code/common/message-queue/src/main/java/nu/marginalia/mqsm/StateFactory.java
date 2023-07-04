package nu.marginalia.mqsm;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
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

    public <T> MachineState create(String name, Class<T> param, Function<T, StateTransition> logic) {
        return new MachineState() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public StateTransition next(String message) {
                return logic.apply(gson.fromJson(message, param));
            }

            @Override
            public boolean isFinal() {
                return false;
            }
        };
    }

    public MachineState create(String name, Supplier<StateTransition> logic) {
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
            public boolean isFinal() {
                return false;
            }
        };
    }

    public StateTransition transition(String state) {
        return StateTransition.to(state);
    }

    public StateTransition transition(String state, Object message) {
        return StateTransition.to(state, gson.toJson(message));
    }
}
