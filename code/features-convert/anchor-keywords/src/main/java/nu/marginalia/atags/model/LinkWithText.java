package nu.marginalia.atags.model;

public record LinkWithText(String url, String text, String source) {
    public Link toLink() {
        return new Link(source, text);
    }
}
