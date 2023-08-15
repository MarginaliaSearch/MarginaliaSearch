package nu.marginalia.actor;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import nu.marginalia.actor.state.ActorResumeBehavior;
import nu.marginalia.actor.state.ActorStateInstance;
import nu.marginalia.actor.state.ActorStateTransition;

import java.util.function.Function;
import java.util.function.Supplier;

/** Factory for creating actor state instances. You probably don't want to use this directly.
 * <p>
 * Use AbstractStatePrototype instead. */
public class ActorStateFactory {
    private final Gson gson;

    public ActorStateFactory(Gson gson) {
        this.gson = gson;
    }

    public <T> ActorStateInstance create(String name, ActorResumeBehavior resumeBehavior, Class<T> param, Function<T, ActorStateTransition> logic) {
        return new ActorStateInstance() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public ActorStateTransition next(String message) {

                if (message.isEmpty()) {
                    return logic.apply(null);
                }

                try {
                    var paramObj = gson.fromJson(message, param);
                    return logic.apply(paramObj);
                }
                catch (JsonSyntaxException ex) {
                    throw new IllegalArgumentException("Failed to parse '" + message +
                                "' into a '" + param.getSimpleName() + "'", ex);
                }
            }

            @Override
            public ActorResumeBehavior resumeBehavior() {
                return resumeBehavior;
            }

            @Override
            public boolean isFinal() {
                return false;
            }
        };
    }

    public ActorStateInstance create(String name, ActorResumeBehavior actorResumeBehavior, Supplier<ActorStateTransition> logic) {
        return new ActorStateInstance() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public ActorStateTransition next(String message) {
                return logic.get();
            }

            @Override
            public ActorResumeBehavior resumeBehavior() {
                return actorResumeBehavior;
            }

            @Override
            public boolean isFinal() {
                return false;
            }
        };
    }

    public ActorStateTransition transition(String state) {
        return ActorStateTransition.to(state);
    }

    public ActorStateTransition transition(String state, Object message) {

        if (null == message) {
            return ActorStateTransition.to(state);
        }

        return ActorStateTransition.to(state, gson.toJson(message));
    }

    static class ErrorStateInstance implements ActorStateInstance {
        @Override
        public String name() { return "ERROR"; }

        @Override
        public ActorStateTransition next(String message) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ActorResumeBehavior resumeBehavior() { return ActorResumeBehavior.RETRY; }

        @Override
        public boolean isFinal() { return true; }
    }

    static class FinalState implements ActorStateInstance {
        @Override
        public String name() { return "END"; }

        @Override
        public ActorStateTransition next(String message) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ActorResumeBehavior resumeBehavior() { return ActorResumeBehavior.RETRY; }

        @Override
        public boolean isFinal() { return true; }
    }

    static class ResumingState implements ActorStateInstance {
        @Override
        public String name() { return "RESUMING"; }

        @Override
        public ActorStateTransition next(String message) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ActorResumeBehavior resumeBehavior() { return ActorResumeBehavior.RETRY; }

        @Override
        public boolean isFinal() { return false; }
    }
}
