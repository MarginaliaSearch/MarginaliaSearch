package nu.marginalia.service.discovery.property;

import java.net.*;
import java.util.UUID;

public record ServiceEndpoint(String host, int port) {

    public static ServiceEndpoint parse(String hostAndPort) {
        var parts = hostAndPort.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid host:port string: " + hostAndPort);
        }
        return new ServiceEndpoint(parts[0], Integer.parseInt(parts[1]));
    }

    public URL toURL(String endpoint, String query) throws URISyntaxException, MalformedURLException {
        return new URI("http", null, host, port, endpoint, query, null)
                .toURL();
    }
    public InetSocketAddress toInetSocketAddress() {
         return new InetSocketAddress(host(), port());
    }

    /** Validate the host by checking if it is a valid IP address or a hostname that can be resolved.
     *
     * @return true if the host is a valid
     */
    public boolean validateHost() {
        try {
            // Throws UnknownHostException if the host is not a valid IP address or hostname
            // (this should not be slow since the DNS lookup should be local, and if it isn't;
            // should be cached by the OS or the JVM)
            InetAddress.getByName(host());
            return true;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    public InstanceAddress asInstance(UUID instance, long cxTime) {
        return new InstanceAddress(this, instance, cxTime);
    }

    public record InstanceAddress(ServiceEndpoint endpoint, UUID instance, long cxTime) {
        public String host() {
            return endpoint.host();
        }
        public int port() {
            return endpoint.port();
        }
    }
}
