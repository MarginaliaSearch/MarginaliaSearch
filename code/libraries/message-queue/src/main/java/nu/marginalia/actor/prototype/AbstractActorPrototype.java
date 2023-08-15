package nu.marginalia.actor.prototype;

import nu.marginalia.actor.ActorStateFactory;
import nu.marginalia.actor.state.ActorState;
import nu.marginalia.actor.state.ActorResumeBehavior;
import nu.marginalia.actor.state.ActorTerminalState;
import nu.marginalia.actor.state.ActorStateInstance;
import nu.marginalia.actor.state.ActorStateTransition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

/** Base class for actors. The state graph is defined using public methods
 * annotated with {@code @ActorState} and {@code @ActorTerminalState}.  This class provide
 * a mediation layer that translates these annotations into a state graph
 * that can be used by the actor runtime.
 * <p> . <p>
 * <pre>
 * public class MyActor extends AbstractActorPrototype {
 *   {@code @ActorState(name="INITIAL", next="STATE_1")}
 *   public void initial() { ... }
 *   {@code @ActorState(name="STATE_1", next="STATE_N")}
 *   public void state1() { ... }
 *   ...
 * }
 * </pre>
 * <p>
 * The prototype provides explicit transition() and error() methods that can be used
 * to jump to a different state. Each of these methods come with a variant that has a
 * parameter.  The parameter will be passed as a payload to the next state.
 * </p>
 * <p>The @ActorState annotation also provides a default next
 * state that will be transitioned to automatically when the method returns.  If the
 * method returns a value, this value will be passed as a payload to the next state,
 * and injected as a parameter to the handler method.</p>
 * <h2>Caveat</h2>
 * The jump functions are implemented using exceptions.  This means that if you have
 * a {@code try {} catch(Exception e)} block in your code or a {@code @SneakyThrows}
 * annotation, you will catch the exception and prevent the transition.
 *
 */
public abstract class AbstractActorPrototype implements ActorPrototype {
    private final ActorStateFactory stateFactory;
    private static final Logger logger = LoggerFactory.getLogger(AbstractActorPrototype.class);

    public AbstractActorPrototype(ActorStateFactory stateFactory) {
        this.stateFactory = stateFactory;
    }

    /** Explicitly transition to a different state.
     * <p>
     * Caveat: This is implemented via an exception. Mind your catch statements.  */
    public void transition(String state) {
        throw new ControlFlowException(state, null);
    }

    /** Explicitly transition to a different state, encoding a payload.
     * <p>
     * Caveat: This is implemented via an exception. Mind your catch statements.  */
    public <T> void transition(String state, T payload) {
        throw new ControlFlowException(state, payload);
    }

    /** Explicitly transition to the error state.
     * <p>
     * Caveat: This is implemented via an exception. Mind your catch statements.  */
    public void error() {
        throw new ControlFlowException("ERROR", "");
    }

    /** Explicitly transition to the error state with an error message.
     * <p>
     * Caveat: This is implemented via an exception. Mind your catch statements.  */
    public <T> void error(T payload) {
        throw new ControlFlowException("ERROR", payload);
    }

    /** Explicitly transition to the error state.
     * <p>
     * Caveat: This is implemented via an exception. Mind your catch statements.  */
    public void error(Exception ex) {
        throw new ControlFlowException("ERROR", ex.getClass().getSimpleName() + ":" + ex.getMessage());
    }

    @Override
    public boolean isDirectlyInitializable() {
        for (var method : getClass().getMethods()) {
            var gs = method.getAnnotation(ActorState.class);
            if (gs == null) {
                continue;
            }
            if ("INITIAL".equals(gs.name()) && method.getParameterCount() == 0) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Map<String, ActorState> declaredStates() {
        Map<String, ActorState> ret = new HashMap<>();

        for (var method : getClass().getMethods()) {
            var gs = method.getAnnotation(ActorState.class);
            if (gs != null) {
                ret.put(gs.name(), gs);
            }
        }

        return ret;
    }

    /** Compile a list of ActorStateInstances from the @ActorState and @ActorTerminalState annotations.
     */
    @Override
    public List<ActorStateInstance> asStateList() {
        List<ActorStateInstance> ret = new ArrayList<>();

        for (var method : getClass().getMethods()) {
            var gs = method.getAnnotation(ActorState.class);
            if (gs != null) {
                ret.add(createStateInstance(method, gs));
            }

            var ts = method.getAnnotation(ActorTerminalState.class);
            if (ts != null) {
                ret.add(createTerminalStateInstance(ts));
            }
        }

        return ret;
    }

    private ActorStateInstance createStateInstance(Method method, ActorState gs) {

        var parameters =  method.getParameterTypes();
        boolean returnsVoid = method.getGenericReturnType().equals(Void.TYPE);

        if (parameters.length == 0) {
            return stateFactory.create(gs.name(), gs.resume(), () -> {
                try {
                    if (returnsVoid) {
                        method.invoke(this);
                        return ActorStateTransition.to(gs.next());
                    } else {
                        Object ret = method.invoke(this);
                        return stateFactory.transition(gs.next(), ret);
                    }
                }
                catch (Exception e) {
                    return translateInvocationExceptionToStateTransition(gs.name(), e);
                }
            });
        }
        else if (parameters.length == 1) {
            return stateFactory.create(gs.name(), gs.resume(), parameters[0], (param) -> {
                try {
                    if (returnsVoid) {
                        method.invoke(this, param);
                        return ActorStateTransition.to(gs.next());
                    } else {
                        Object ret = method.invoke(this, param);
                        return stateFactory.transition(gs.next(), ret);
                    }
                }
                catch (Exception e) {
                    return translateInvocationExceptionToStateTransition(gs.name(), e);
                }
            });
        }
        else {
            // We permit only @ActorState-annotated methods like this:
            //
            // void foo();
            // void foo(Object bar);
            // Object foo();
            // Object foo(Object bar);

            throw new IllegalStateException("ActorStatePrototype " +
                    getClass().getSimpleName() +
                    " has invalid method signature for method " +
                    method.getName() +
                    ": Expected 0 or 1 parameter(s) but found " +
                    Arrays.toString(parameters));
        }
    }

    private ActorStateInstance createTerminalStateInstance(ActorTerminalState ts) {
        final String name = ts.name();
        return stateFactory.create(name, ActorResumeBehavior.ERROR, () -> {
            throw new ControlFlowException(name, null);
        });
    }

    private ActorStateTransition translateInvocationExceptionToStateTransition(String state, Throwable ex) {
        while (ex instanceof InvocationTargetException e) {
            if (e.getCause() != null) ex = ex.getCause();
        }

        if (ex instanceof ControlFlowException cfe) {
            return stateFactory.transition(cfe.getState(), cfe.getPayload());
        }
        else if (ex instanceof InterruptedException intE) {
            logger.error("State execution was interrupted " + state);
            return ActorStateTransition.to("ERR", "Execution interrupted");
        }
        else {
            logger.error("Error in state invocation " + state, ex);
            return ActorStateTransition.to("ERROR",
                    "Exception: " + ex.getClass().getSimpleName() + "/" +  ex.getMessage());
        }
    }

    /** Exception thrown by a state to indicate that the state machine should jump to a different state. */
    public static class ControlFlowException extends RuntimeException {
        private final String state;
        private final Object payload;

        public ControlFlowException(String state, Object payload) {
            this.state = state;
            this.payload = payload;
        }

        public String getState() {
            return state;
        }

        public Object getPayload() {
            return payload;
        }

        public StackTraceElement[] getStackTrace() { return new StackTraceElement[0]; }
    }
}
