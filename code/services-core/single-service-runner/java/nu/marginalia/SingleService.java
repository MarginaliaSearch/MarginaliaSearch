package nu.marginalia;

public class SingleService {
    enum Service {
        IndexService("index", "nu.marginalia.index.IndexMain"),
        ControlService("control", "nu.marginalia.control.ControlMain"),
        ExecutorService("executor", "nu.marginalia.executor.ExecutorMain"),
        QueryService("query", "nu.marginalia.query.QueryMain"),
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

    public static void main(String... args) {
        if (args.length == 0) {
            System.out.println("Usage: SingleService <service> [args...]");
        }

        String serviceName = args[0];
        String[] serviceArgs = new String[args.length - 1];
        System.arraycopy(args, 1, serviceArgs, 0, serviceArgs.length);

        for (var service : Service.values()) {
            if (service.name.equals(serviceName)) {
                service.run(serviceArgs);
            }
        }
    }
}
