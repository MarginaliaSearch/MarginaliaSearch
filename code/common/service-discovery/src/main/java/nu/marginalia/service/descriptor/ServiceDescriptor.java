package nu.marginalia.service.descriptor;

import nu.marginalia.service.id.ServiceId;

public class ServiceDescriptor {
    public final ServiceId id;
    public final String name;
    public final int port;

    public ServiceDescriptor(ServiceId id, int port) {
        this.id = id;
        this.name = id.name;
        this.port = port;
    }
    public ServiceDescriptor(ServiceId id, String host, int port) {
        this.id = id;
        this.name = host;
        this.port = port;
    }
    public String toString() {
        return name;
    }

    public String describeService() {
        return String.format("%s", name);
    }
}
