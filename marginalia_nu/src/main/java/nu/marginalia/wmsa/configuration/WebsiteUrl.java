package nu.marginalia.wmsa.configuration;

public record WebsiteUrl(String url) {
    public String withPath(String path) {
        return url + path;
    }
}
