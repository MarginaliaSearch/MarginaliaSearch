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
    public List<ActorStateInstance> asStateList() {

        List<ActorStateInstance> steps = new ArrayList<>();

        // Look for member classes that instantiate ActorStep in this class and all parent classes up until
        // RecordActorPrototype

        for (Class<?> clazz = getClass();
             RecordActorPrototype.class.isAssignableFrom(clazz);
             clazz = clazz.getSuperclass())
        {
            for (var stepClass : clazz.getDeclaredClasses()) {
                if (!ActorStep.class.isAssignableFrom(stepClass))
                    continue;
                steps.add(new StepInstance((Class<? extends ActorStep>) stepClass));
            }
        }

        return steps;
    }

    private class StepInstance implements ActorStateInstance {
        private final Class<? extends ActorStep> stepClass;

        public StepInstance(Class<? extends ActorStep> stepClass) {
            this.stepClass = stepClass;
        }
        @Override
        public String name() {
            return stepClass.getSimpleName().toUpperCase();
        }

        @Override
        public ActorStateTransition next(String message) {
            try {
                ActorStep dest;
                if (null == message || message.isBlank()) {
                    dest = stepClass.getDeclaredConstructor().newInstance();
                } else {
                    dest = gson.fromJson(message, stepClass);
                }
                dest = transition(dest);
                return new ActorStateTransition(
                        dest.getClass().getSimpleName().toUpperCase(),
                        gson.toJson(dest)
                );
            } catch (ActorControlFlowException cfe) {
                return new ActorStateTransition(
                        Error.class.getSimpleName(),
                        gson.toJson(new Error(cfe.getMessage()))
                );
            } catch (Exception ex) {
                logger.error("Error in transition handler, decoding  {}:'{}'", stepClass.getSimpleName(), message);
                logger.error("Exception was", ex);

                return new ActorStateTransition("ERROR", ex.getMessage());
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
