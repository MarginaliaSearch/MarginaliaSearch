package nu.marginalia.actor.state;

public interface ActorStep {
    static String functionName(Class<? extends ActorStep> type) {
        return type.getSimpleName().toUpperCase();
    }
}
