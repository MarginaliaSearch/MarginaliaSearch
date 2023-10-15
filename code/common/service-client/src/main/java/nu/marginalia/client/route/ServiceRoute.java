package nu.marginalia.client.route;

import org.apache.http.HttpHost;

public record ServiceRoute(String hostname, int port) {
    public String toString() {
        if (port == 80) {
            return "http://" + hostname;
        }
        return new HttpHost(hostname(), port()).toURI();
    }
}
