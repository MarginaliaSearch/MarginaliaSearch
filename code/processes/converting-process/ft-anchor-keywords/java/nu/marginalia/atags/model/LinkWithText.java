package nu.marginalia.atags.model;

public record LinkWithText(String url, String text, int cnt) {
    public Link toLink() {
        return new Link(text, cnt);
    }
}
