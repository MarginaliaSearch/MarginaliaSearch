package nu.marginalia.control.model;

public record ControlProcessState(String name, String state, boolean terminal) {
    public String stateIcon() {
        if (terminal) {
            return "\uD83D\uDE34";
        }
        else {
            return "\uD83C\uDFC3";
        }
    }
}
