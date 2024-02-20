package nu.marginalia.service.discovery.property;


import lombok.SneakyThrows;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.util.UUID;

public sealed interface ServiceEndpoint {
    String host();
    int port();

    URL toURL(String endpoint, String query);
    default InetSocketAddress toInetSocketAddress() {
         return new InetSocketAddress(host(), port());
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
