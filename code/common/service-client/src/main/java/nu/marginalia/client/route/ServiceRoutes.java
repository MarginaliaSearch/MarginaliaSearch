package nu.marginalia.client.route;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class ServiceRoutes {
    private final ConcurrentHashMap<Integer, ServiceRoute> knownRoutes = new ConcurrentHashMap<>();
    private final RouteProvider provider;

    public ServiceRoutes(RouteProvider provider) {
        this.provider = provider;
    }

    public ServiceRoute get(int node) {
        return knownRoutes.computeIfAbsent(node, provider::findRoute);
    }

    public List<Integer> getNodes() {
        return new ArrayList<>(knownRoutes.keySet());
    }
}
