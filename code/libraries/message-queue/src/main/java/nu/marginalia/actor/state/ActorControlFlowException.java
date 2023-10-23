package nu.marginalia.actor.state;

/** Throwing this exception within RecordActorPrototype's transition method is equivalent to
 * yielding new Error(message).
 */
public class ActorControlFlowException extends Exception {
    public ActorControlFlowException(String message) {
        super(message);
    }
}
