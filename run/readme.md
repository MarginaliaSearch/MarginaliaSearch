# Run

When developing locally, this directory will contain run-time data required for
the search engine. In a clean check-out, it only contains the tools required to 
bootstrap this directory structure.

## Set up

While the system is designed to run bare metal in production,
for local development, you're strongly encouraged to use docker
or podman. 

To go from a clean check out of the git repo to a running search engine,
follow these steps. You're assumed to sit in the project root the whole time.

1. Run the one-time setup, it will create the
basic runtime directory structure and download some models and data that doesn't
come with the git repo.

```
$ run/setup.sh
```

2. Compile the project and build docker images

```
$ ./gradlew assemble docker
```

3. Download a sample of crawl data, process it and stick the metadata
into the database. The data is only downloaded once. Grab a cup of coffee, this takes a few minutes. 
This needs to be done whenever the crawler or processor has changed. 

```
$ docker-compose up -d mariadb
$ run/reconvert.sh
```

4. Bring the system online. We'll run it in the foreground in the terminal this time
because it's educational to see the logs. Add `-d` to run in the background.


```
$ docker-compose up
```

5.  Since we've just processed new crawl data, the system needs to construct static
indexes. Wait for the line 'Auto-conversion finished!'  

When all is done, it should be possible to visit
[http://localhost:8080](http://localhost:8080) and try a few searches!