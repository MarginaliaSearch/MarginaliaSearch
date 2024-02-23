package nu.marginalia.actor.prototype;

import nu.marginalia.actor.state.ActorStateInstance;

import java.util.List;

public interface ActorPrototype {
    /**
     * User-facing description of the actor.
     */
    String describe();

    /** Check whether there is an INITIAL state that can be directly initialized
     * without declared parameters. */
    boolean isDirectlyInitializable();

    /** Get or create a list of ActorStateInstances */
    List<ActorStateInstance> asStateList();
}
