package nu.marginalia.service.id;

public enum ServiceId {

    Assistant("assistant-service"),
    Api("api-service"),
    Search("search-service"),
    Index("index-service"),

    Dating("dating-service"),
    Explorer("explorer-service"),

    Other_Auth("auth"),
    Other_Memex("memex"),


    Other_ResourceStore("resource-store"),
    Other_Renderer("renderer"),
    Other_PodcastScraper("podcast-scraper");

    public final String name;
    ServiceId(String name) {
        this.name = name;
    }
}
