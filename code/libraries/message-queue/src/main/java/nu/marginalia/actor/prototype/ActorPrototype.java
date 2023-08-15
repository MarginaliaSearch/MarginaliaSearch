package nu.marginalia.actor.prototype;

import nu.marginalia.actor.state.ActorState;
import nu.marginalia.actor.state.ActorStateInstance;
import nu.marginalia.actor.state.ActorTerminalState;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ActorPrototype {
    /**
     * User-facing description of the actor.
     */
    String describe();

    /** Check whether there is an INITIAL state that can be directly initialized
     * without declared parameters. */
    boolean isDirectlyInitializable();

    Map<String, ActorState> declaredStates();

    /** Get or create a list of ActorStateInstances */
    List<ActorStateInstance> asStateList();
}
