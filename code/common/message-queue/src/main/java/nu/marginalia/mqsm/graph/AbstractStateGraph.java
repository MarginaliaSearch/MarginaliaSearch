package nu.marginalia.mqsm.graph;

import nu.marginalia.mqsm.state.MachineState;
import nu.marginalia.mqsm.StateFactory;
import nu.marginalia.mqsm.state.StateTransition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

public abstract class AbstractStateGraph {
    private final StateFactory stateFactory;
    private static final Logger logger = LoggerFactory.getLogger(AbstractStateGraph.class);

    public AbstractStateGraph(StateFactory stateFactory) {
        this.stateFactory = stateFactory;
    }

    /** User-facing description of the actor. */
    public abstract String describe();

    public void transition(String state) {
        throw new ControlFlowException(state, null);
    }

    public <T> void transition(String state, T payload) {
        throw new ControlFlowException(state, payload);
    }

    public void error() {
        throw new ControlFlowException("ERROR", "");
    }

    public <T> void error(T payload) {
        throw new ControlFlowException("ERROR", payload);
    }

    public void error(Exception ex) {
        throw new ControlFlowException("ERROR", ex.getClass().getSimpleName() + ":" + ex.getMessage());
    }

    /** Check whether there is an INITIAL state that can be directly initialized
     * without declared parameters. */
    public boolean isDirectlyInitializable() {
        for (var method : getClass().getMethods()) {
            var gs = method.getAnnotation(GraphState.class);
            if (gs == null) {
                continue;
            }
            if ("INITIAL".equals(gs.name()) && method.getParameterCount() == 0) {
                return true;
            }
        }
        return false;
    }

    public Map<String, GraphState> declaredStates() {
        Map<String, GraphState> ret = new HashMap<>();

        for (var method : getClass().getMethods()) {
            var gs = method.getAnnotation(GraphState.class);
            if (gs != null) {
                ret.put(gs.name(), gs);
            }
        }

        return ret;
    }


    public Set<TerminalGraphState> terminalStates() {
        Set<TerminalGraphState> ret = new HashSet<>();

        for (var method : getClass().getMethods()) {
            var gs = method.getAnnotation(TerminalGraphState.class);
            if (gs != null) {
                ret.add(gs);
            }
        }

        return ret;
    }

    public List<MachineState> asStateList() {
        List<MachineState> ret = new ArrayList<>();

        for (var method : getClass().getMethods()) {
            var gs = method.getAnnotation(GraphState.class);
            if (gs != null) {
                ret.add(graphState(method, gs));
            }

            var ts = method.getAnnotation(TerminalGraphState.class);
            if (ts != null) {
                ret.add(stateFactory.create(ts.name(), ResumeBehavior.ERROR, () -> {
                    throw new ControlFlowException(ts.name(), null);
                }));
            }
        }

        return ret;
    }

    private MachineState graphState(Method method, GraphState gs) {

        var parameters =  method.getParameterTypes();
        boolean returnsVoid = method.getGenericReturnType().equals(Void.TYPE);

        if (parameters.length == 0) {
            return stateFactory.create(gs.name(), gs.resume(), () -> {
                try {
                    if (returnsVoid) {
                        method.invoke(this);
                        return StateTransition.to(gs.next());
                    } else {
                        Object ret = method.invoke(this);
                        return stateFactory.transition(gs.next(), ret);
                    }
                }
                catch (Exception e) {
                    return invocationExceptionToStateTransition(gs.name(), e);
                }
            });
        }
        else if (parameters.length == 1) {
            return stateFactory.create(gs.name(), gs.resume(), parameters[0], (param) -> {
                try {
                    if (returnsVoid) {
                        method.invoke(this,  param);
                        return StateTransition.to(gs.next());
                    } else {
                        Object ret = method.invoke(this, param);
                        return stateFactory.transition(gs.next(), ret);
                    }
                } catch (Exception e) {
                    return invocationExceptionToStateTransition(gs.name(), e);
                }
            });
        }
        else {
            // We permit only @GraphState-annotated methods like this:
            //
            // void foo();
            // void foo(Object bar);
            // Object foo();
            // Object foo(Object bar);

            throw new IllegalStateException("StateGraph " +
                    getClass().getSimpleName() +
                    " has invalid method signature for method " +
                    method.getName() +
                    ": Expected 0 or 1 parameter(s) but found " +
                    Arrays.toString(parameters));
        }
    }

    private StateTransition invocationExceptionToStateTransition(String state, Throwable ex) {
        while (ex instanceof InvocationTargetException e) {
            if (e.getCause() != null) ex = ex.getCause();
        }

        if (ex instanceof ControlFlowException cfe) {
            return stateFactory.transition(cfe.getState(), cfe.getPayload());
        }
        else if (ex instanceof InterruptedException intE) {
            logger.error("State execution was interrupted " + state);
            return StateTransition.to("ERR", "Execution interrupted");
        }
        else {
            logger.error("Error in state invocation " + state, ex);
            return StateTransition.to("ERROR",
                    "Exception: " + ex.getClass().getSimpleName() + "/" +  ex.getMessage());
        }
    }

}
