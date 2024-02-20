package nu.marginalia.service.discovery.property;


import lombok.SneakyThrows;

import java.net.*;
import java.util.UUID;

public sealed interface ServiceEndpoint {
    String host();
    int port();

    URL toURL(String endpoint, String query);
    default InetSocketAddress toInetSocketAddress() {
         return new InetSocketAddress(host(), port());
    }

    /** Validate the host by checking if it is a valid IP address or a hostname that can be resolved.
     *
     * @return true if the host is a valid
     */
    default boolean validateHost() {
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

    static ServiceEndpoint forSchema(ApiSchema schema, String host, int port) {
        return switch (schema) {
            case REST -> new RestEndpoint(host, port);
            case GRPC -> new GrpcEndpoint(host, port);
        };
    }

    record RestEndpoint(String host, int port) implements ServiceEndpoint {
        public static RestEndpoint parse(String hostColonPort) {
            String[] parts = hostColonPort.split(":");

            if (parts.length != 2) {
                throw new IllegalArgumentException(STR."Invalid host:port-format '\{hostColonPort}'");
            }

            return new RestEndpoint(
                    parts[0],
                    Integer.parseInt(parts[1])
            );
        }

        @SneakyThrows
        public URL toURL(String endpoint, String query) {
            return new URI("http", null, host, port, endpoint, query, null)
                    .toURL();
        }

        public InstanceAddress<RestEndpoint> asInstance(UUID uuid) {
            return new InstanceAddress<>(this, uuid);
        }
    }

    record GrpcEndpoint(String host, int port) implements ServiceEndpoint {
        public static GrpcEndpoint parse(String hostColonPort) {
            String[] parts = hostColonPort.split(":");

            if (parts.length != 2) {
                throw new IllegalArgumentException(STR."Invalid host:port-format '\{hostColonPort}'");
            }

            return new GrpcEndpoint(
                    parts[0],
                    Integer.parseInt(parts[1])
            );
        }

        public InstanceAddress<GrpcEndpoint> asInstance(UUID uuid) {
            return new InstanceAddress<>(this, uuid);
        }

        @Override
        public URL toURL(String endpoint, String query) {
            throw new UnsupportedOperationException();
        }
    }

    record InstanceAddress<T extends ServiceEndpoint>(T endpoint, UUID instance) {
        public String host() {
            return endpoint.host();
        }
        public int port() {
            return endpoint.port();
        }
    }
}
