# DB

This module primarily contains SQL files for the URLs database. The most central tables are `EC_DOMAIN`, `EC_URL` and `EC_PAGE_DATA`.

## Flyway

The system uses flyway to track database changes and allow easy migrations, this is accessible via gradle tasks.

* `flywayMigrate`
* `flywayBaseline`
* `flywayRepair`
* `flywayClean` (dangerous as in wipes your entire database)

Refer to the [Flyway documentation](https://documentation.red-gate.com/fd/flyway-documentation-138346877.html) for guidance.
It's well documented and these are probably the only four tasks you'll ever need.

If you are not running the system via docker, you need to provide alternative connection details than
the defaults (TODO: how?).

The migration files are in [resources/db/migration](src/main/resources/db/migration).  The file name convention
incorporates the project's cal-ver versioning; and are applied in lexicographical order.

    VYY_MM_v_nnn__description.sql

## Central Paths

* [migrations](src/main/resources/db/migration) - Flyway migrations

## See Also 

* [common/service](../service) implements DatabaseModule, which is from where the services get database connections.
