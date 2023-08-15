package nu.marginalia.actor.state;

public interface ActorStateInstance {
    String name();

    ActorStateTransition next(String message);

    ActorResumeBehavior resumeBehavior();

    boolean isFinal();

}
