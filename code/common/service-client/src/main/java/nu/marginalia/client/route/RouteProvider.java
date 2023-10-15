package nu.marginalia.client.route;

import nu.marginalia.service.descriptor.ServiceDescriptor;

public interface RouteProvider {
    ServiceRoute findRoute(int node);

    static RouteProvider fromService(ServiceDescriptor serviceDescriptor) {
        return (n) -> new ServiceRoute(serviceDescriptor.getHostName(n), 80);
    }
}
