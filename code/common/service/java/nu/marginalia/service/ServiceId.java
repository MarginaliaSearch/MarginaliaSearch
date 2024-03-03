package nu.marginalia.service;

public enum ServiceId {

    Assistant("assistant-service"),
    Api("api-service"),
    Search("search-service"),
    Index("index-service"),
    Query("query-service"),
    Executor("executor-service"),

    Control("control-service"),

    Dating("dating-service"),
    Explorer("explorer-service");

    public final String serviceName;

    ServiceId(String serviceName) {
        this.serviceName = serviceName;
    }

    public String withNode(int node) {
        return serviceName + ":" + node;
    }

    public static ServiceId byName(String name) {
        for (ServiceId id : values()) {
            if (id.serviceName.equals(name)) {
                return id;
            }
        }
        return null;
    }
}
