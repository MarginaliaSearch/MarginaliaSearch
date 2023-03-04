# Run

When developing locally, this directory will contain run-time data required for
the search engine. In a clean check-out, it only contains the tools required to 
bootstrap this directory structure.

## Set up

While the system is designed to run bare metal in production,
for local development, you're strongly encouraged to use docker
or podman. 

From a fresh to running system, you'll need to do this:

From the project root
```
$ run/setup.sh

$ ./gradlew assemble docker

$ docker-compose up -d mariadb

$ run/reconvert.sh

$ docker-compose up
```

Wait a moment and check out [http://localhost:8080](http://localhost:8080).
