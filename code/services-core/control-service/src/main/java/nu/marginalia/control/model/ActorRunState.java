package nu.marginalia.control.model;

public record ActorRunState(String name, String state, boolean terminal, boolean canStart) {
    public String stateIcon() {
        if (terminal) {
            return "\uD83D\uDE34";
        }
        else if (state.equals("MONITOR")) {
            return "\uD83D\uDD26";
        }
        else if (state.endsWith("WAIT") || state.endsWith("REPLY")) {
            return "\uD83D\uDD59";
        }
        else {
            return "\uD83C\uDFC3";
        }
    }
}
