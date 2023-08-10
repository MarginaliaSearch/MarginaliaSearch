package nu.marginalia.service.id;

public enum ServiceId {

    Assistant("assistant-service"),
    Api("api-service"),
    Search("search-service"),
    Index("index-service"),

    Control("control-service"),

    Dating("dating-service"),
    Explorer("explorer-service");

    public final String name;
    ServiceId(String name) {
        this.name = name;
    }

    public static ServiceId byName(String name) {
        for (ServiceId id : values()) {
            if (id.name.equals(name)) {
                return id;
            }
        }
        return null;
    }
}
