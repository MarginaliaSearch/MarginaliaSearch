package nu.marginalia.ddtrackergradar.model;

public record DDGTOwner(String name, String displayName, String privacyPolicy, String url) {
    public boolean isEmpty() {
        return name == null
                && displayName == null
                && privacyPolicy == null
                && url == null;
    }
}
