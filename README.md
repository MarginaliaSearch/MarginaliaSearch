# marginalia.nu

This is the source code for [Marginalia Search](https://search.marginalia.nu). 

The aim of the project is to develop new and alternative discovery methods for the Internet. 
It's an experimental workshop as much as it is a public service, the overarching goal is to
elevate the more human, non-commercial sides of the Internet. A side-goal is to do this without
requiring datacenters and expensive enterprise hardware, to run this operation on affordable hardware.

## Set up instructions

For local development, you're strongly encouraged to use docker or podman.
From a fresh to running system, you'll need to do this:

```
$ ./gradlew assemble

$ ./gradlew docker

$ vim run/settings.profile

(follow instructions in file)

$ run/setup.sh

$ run/reconvert.sh

$ docker-compose up
```

Wait a moment and check out [https://localhost:8080](https://localhost:8080).

## Documentation

Documentation is a work in progress.

## Contributing

[CONTRIBUTING.md](CONTRIBUTING.md)

## Supporting

Consider [supporting this project](https://memex.marginalia.nu/projects/edge/supporting.gmi).

## Contact

You can email <kontakt@marginalia.nu> with any questions or feedback.
