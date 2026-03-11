# Test Helpers

Shared utilities and base classes for tests across the project.

## TestUtil

File system helpers for tests, such as `clearTempDir(Path)` for recursive
temporary directory cleanup.

## TestMigrationLoader

Loads and runs database migrations in test contexts. Provides both full
Flyway-based migration (`flywayMigration`) and selective script loading
(`loadMigration`, `loadMigrations`) for tests that need specific schema
subsets.

## AbstractDockerServiceTest

Base class for Docker integration tests that verify service containers
boot and function correctly. Uses Testcontainers to orchestrate a full
environment with MariaDB, Zookeeper, and the service container. Verifies
health endpoints and database event logging.

Extend it and pass the service name to the constructor:

```java
public class MyServiceDockerTest extends AbstractDockerServiceTest {
    public MyServiceDockerTest() {
        super("my-service");
    }
}
```

For services that create the `/first-boot` znode themselves (e.g. control-service),
pass `FirstBoot.EXPECT_SERVICE_CREATES` as the second constructor argument.

Tests are annotated with `@Tag("docker")` and run via the `dockerTests`
Gradle task. The service image must be built beforehand via `./gradlew docker`.
