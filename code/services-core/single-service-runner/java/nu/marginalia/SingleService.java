package nu.marginalia;

/** Springboard for launching services outside of docker */
public class SingleService {

    public static void main(String... args) {
        if (!configure(args)) {
            System.out.println("Usage: SingleService <service>:node bind-address:bind-port-http:bind-port-grpc announce-address [args...]");
            return;
        }

        requireEnv("WMSA_HOME", "Path to the install directory of the project");

        String serviceName = args[0].substring(0, args[0].indexOf(":"));
        String[] serviceArgs = new String[args.length - 3];
        System.arraycopy(args, 3, serviceArgs, 0, serviceArgs.length);

        for (var service : Service.values()) {
            if (service.name.equals(serviceName)) {
                service.run(serviceArgs);
            }
        }
    }

    private static void requireEnv(String env, String desc) {
        if (System.getenv(env) == null) {
            throw new IllegalArgumentException("Missing environment variable: " + env + " - " + desc);
        }
        else {
            System.out.println("Found environment variable: " + env + " = " + System.getenv(env));
        }
    }

    /** Set system properties for the address and ports for the service.
     *
     * @return true if the configuration was successful
     * */
    private static boolean configure(String[] args) {
        if (args.length < 3)
            return false;

        String serviceNameAndNode = args[0];
        String[] nameAndNode = serviceNameAndNode.split(":");
        if (nameAndNode.length != 2)
            return false;

        try {
            final String bindAddress_http_grpc = args[1];
            final String announceAddress = args[2];

            final String[] bindParts = bindAddress_http_grpc.split(":");
            if (bindParts.length < 3)
                return false;

            String bindAddress = bindParts[0];

            int httpPort = Integer.parseInt(bindParts[1]);
            int grpcPort = Integer.parseInt(bindParts[2]);

            System.out.println("Configuring service with bind address: " + bindAddress + "\n                         http port: " + httpPort + "\n                         grpc port: " + grpcPort + "\n                         announce address: " + announceAddress + "\n");

            System.setProperty("service.bind-address", bindAddress);
            System.setProperty("service.http-port", Integer.toString(httpPort));
            System.setProperty("service.grpc-port", Integer.toString(grpcPort));
            System.setProperty("service.host", announceAddress);

            // By default, disable prometheus metrics
            if (System.getProperty("service.prometheus-port") == null) {
                System.setProperty("service.prometheus-port", "-1");
            }

            System.setProperty("system.serviceNode", nameAndNode[1]);

            return true;
        }
        catch (NumberFormatException e) {
            return false;
        }

    }

    enum Service {
        IndexService("index", "nu.marginalia.index.IndexMain"),
        ControlService("control", "nu.marginalia.control.ControlMain"),
        ExecutorService("executor", "nu.marginalia.executor.ExecutorMain"),
        QueryService("query", "nu.marginalia.query.QueryMain"),

        AssitantService("assistant", "nu.marginalia.assistant.AssistantMain"),

        SearchService("search", "nu.marginalia.search.SearchMain"),
        ApiService("api", "nu.marginalia.api.ApiMain"),
        ;

        public final String name;
        public final String className;

        Service(String name, String className) {
            this.name = name;
            this.className = className;
        }

        /** Call the main method of the service class */
        public void run(String[] args) {
            try {
                // Use reflection to call the main method of the service class to avoid
                // loading all classes at startup time, which would invoke a bunch of contradictory
                // static initializers

                Class<?> clazz = Class.forName(className);
                clazz.getMethod("main", String[].class).invoke(null, (Object) args);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


}
