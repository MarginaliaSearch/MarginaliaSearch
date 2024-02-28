package nu.marginalia.actor.state;

public record ActorStateTransition(String state, String message) {
    public static ActorStateTransition to(String state) {
        return new ActorStateTransition(state, "");
    }

    public static ActorStateTransition to(String state, String message) {
        return new ActorStateTransition(state, message);
    }
}
