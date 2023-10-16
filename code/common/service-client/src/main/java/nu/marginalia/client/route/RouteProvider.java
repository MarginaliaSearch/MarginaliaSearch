package nu.marginalia.client.route;

import nu.marginalia.service.descriptor.ServiceDescriptor;

public class RouteProvider {
    private static int defaultPort = 80;

    private final ServiceDescriptor descriptor;

    public RouteProvider(ServiceDescriptor descriptor) {
        this.descriptor = descriptor;
    }

    ServiceRoute findRoute(int node) {
        return new ServiceRoute(descriptor.getHostName(node), defaultPort);
    }

    // Access exists for testing
    public static void setDefaultPort(int port) {
        defaultPort = port;
    }
    public static void resetDefaultPort() {
        defaultPort = 80;
    }
}
