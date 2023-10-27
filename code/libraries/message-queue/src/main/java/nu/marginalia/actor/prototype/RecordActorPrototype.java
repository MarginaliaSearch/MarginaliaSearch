package nu.marginalia.actor.prototype;

import com.google.gson.Gson;
import nu.marginalia.actor.state.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class RecordActorPrototype implements ActorPrototype {

    private final Gson gson;
    private static final Logger logger = LoggerFactory.getLogger(ActorPrototype.class);

    public RecordActorPrototype(Gson gson) {
        this.gson = gson;
    }

    @Terminal
    public record End() implements ActorStep {}
    @Terminal
    public record Error(String message) implements ActorStep {
        public Error() { this(""); }
    }


    /** Implements the actor graph transitions.
     * The return value of this method will be persisted into the database message queue
     * and loaded back again before execution.
     * */
    public abstract ActorStep transition(ActorStep self) throws Exception;

    @Override
    public abstract String describe();

    @Override
    public boolean isDirectlyInitializable() {
        // An actor is Directly Initializable if it has a state called Initial with a zero-argument constructor

        for (Class<?> clazz = getClass();
             RecordActorPrototype.class.isAssignableFrom(clazz);
             clazz = clazz.getSuperclass()) {

            if (Arrays.stream(clazz.getDeclaredClasses())
                    .filter(declaredClazz -> declaredClazz.getSimpleName().equals("Initial"))
                    .flatMap(declaredClazz -> Arrays.stream(declaredClazz.getDeclaredConstructors()))
                    .anyMatch(con -> con.getParameterCount() == 0)) {
                return true;
            }
        }
        return false;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ActorStateInstance> asStateList() {

        List<ActorStateInstance> steps = new ArrayList<>();

        // Look for member classes that implement ActorStep in this class and all parent classes up until
        // RecordActorPrototype

        for (Class<?> clazz = getClass();
             RecordActorPrototype.class.isAssignableFrom(clazz);
             clazz = clazz.getSuperclass())
        {
            for (var stepClass : clazz.getDeclaredClasses()) {
                if (!ActorStep.class.isAssignableFrom(stepClass))
                    continue;

                steps.add(new StateInstance((Class<? extends ActorStep>) stepClass));
            }
        }

        return steps;
    }

    private class StateInstance implements ActorStateInstance {
        private final Class<? extends ActorStep> stepClass;

        public StateInstance(Class<? extends ActorStep> stepClass) {
            this.stepClass = stepClass;
        }

        @Override
        public String name() {
            return functionName(stepClass);
        }

        @Override
        public ActorStateTransition next(String message) {
            ActorStep nextState;

            try {
                var currentState = constructState(message);

                nextState = transition(currentState);
            } catch (ActorControlFlowException cfe) {
                // This exception allows custom error messages
                nextState = new Error(cfe.getMessage());
            } catch (Exception ex) {
                logger.error("Error in transition handler, decoding  {}:'{}'", stepClass.getSimpleName(), message);
                logger.error("Exception was", ex);

                nextState = new Error(ex.getMessage());
            }

            return encodeTransition(nextState);
        }

        private ActorStateTransition encodeTransition(ActorStep nextState) {
            return new ActorStateTransition(
                    functionName(nextState.getClass()),
                    gson.toJson(nextState)
            );
        }

        private String functionName(Class<? extends ActorStep> functionClass) {
            return functionClass.getSimpleName().toUpperCase();
        }

        private ActorStep constructState(String message) throws ReflectiveOperationException {
            if (null == message || message.isBlank()) {
                return stepClass.getDeclaredConstructor().newInstance();
            } else {
                return gson.fromJson(message, stepClass);
            }
        }

        @Override
        public ActorResumeBehavior resumeBehavior() {
            var behavior = stepClass.getAnnotation(Resume.class);

            if (null == behavior)
                return ActorResumeBehavior.ERROR;

            return behavior.behavior();
        }

        @Override
        public boolean isFinal() {
            return stepClass.getAnnotation(Terminal.class) != null;
        }
    }

}
