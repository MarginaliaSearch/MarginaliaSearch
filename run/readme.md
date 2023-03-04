# Run

When developing locally, this directory will contain run-time data required for
the search engine. In a clean check-out, it only contains the tools required to 
bootstrap this directory structure.

## Set up

While the system is designed to run bare metal in production,
for local development, you're strongly encouraged to use docker
or podman. 

From a fresh to running system, you'll need to do this:

From the project root run the one-time setup, it will create the
basic runtime directory structure 
```
$ run/setup.sh
```

Next, compile the project and build docker images

```
$ ./gradlew assemble docker
```

Next, download a sample of crawl data, process it and stick the metadata
into the database. The data is only downloaded once. 

Grab a cup of coffee, this takes a few minutes.

This needs to be done whenever the crawler or processor has changed.

```
$ docker-compose up -d mariadb
$ run/reconvert.sh
```

Now we're ready to bring the system online.

```
$ docker-compose up
```

Since we've just processed new crawl data, the system needs to construct static
indexes. This takes a moment. Wait for the line 'Auto-conversion finished!'

When all is done, it should be possible to visit
[http://localhost:8080](http://localhost:8080) and try a few searches!

