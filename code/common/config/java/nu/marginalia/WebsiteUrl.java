package nu.marginalia;

public record WebsiteUrl(String url) {
    public String withPath(String path) {
        return url + path;
    }
}
