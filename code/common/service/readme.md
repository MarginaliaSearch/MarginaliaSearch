# Service

Contains the base classes for the services. This is where port configuration,
and common endpoints are set up.   The service base class is additionally responsible for
registering the service with the service registry, and announcing its liveness.

## Creating a new Service

The minimal service needs a `MainClass` and a `Service` class. 

For proper initiation, the main class should look like the below.  It will
create a Guice injector, and then create an instance of the service, and take
care of loading configuration properties and setting up loggers in a sane
way.

```java
public class FoobarMain extends MainClass {

    @Inject
    public FoobarMain(FoobarService service) {}

    public static void main(String... args) {
        init(ServiceId.Foobar, args);

        Injector injector = Guice.createInjector(
                new FoobarModule(), /* optional custom bindings go here */
                new DatabaseModule(),
                new ConfigurationModule(ServiceId.Foobar));

        injector.getInstance(FoobarMain.class);
        
        // set the service as ready so that delayed tasks can be started
        injector.getInstance(Initialization.class).setReady();
    }
}
```

A service class has a boilerplate set-up that looks like this:

```java
@Singleton
public class FoobarService extends Service {

    @Inject
    public FoobarService(BaseServiceParams params) {
        super(params, List.of(/* grpc services */));
        
        // set up any Spark endpoints here
    }
}
```

The service should also be given a canonical name in the `ServiceId` enum.

## Central Classes

* [MainClass](java/nu/marginalia/service/MainClass.java) bootstraps all executables
* [Service](java/nu/marginalia/service/server/Service.java) base class for all services.