package nu.marginalia.actor.state;

public enum ActorResumeBehavior {
    /** Retry the state on resume */
    RETRY,
    /** Jump to ERROR on resume if the message has been acknowledged */
    ERROR,
    /** Jump to INITIAL on resume */
    RESTART
}
