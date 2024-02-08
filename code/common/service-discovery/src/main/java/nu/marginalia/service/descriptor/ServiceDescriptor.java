package nu.marginalia.service.descriptor;

import nu.marginalia.service.id.ServiceId;

public class ServiceDescriptor {
    public final ServiceId id;
    public final String name;

    public ServiceDescriptor(ServiceId id) {
        this.id = id;
        this.name = id.serviceName;
    }

    public ServiceDescriptor(ServiceId id, String host) {
        this.id = id;
        this.name = host;
    }

    public String getHostName(int node) {
        if (node > 0)
            return name + "-" + node;

        return name;
    }

    public String toString() {
        return name;
    }

    public String describeService() {
        return String.format("%s", name);
    }
}
