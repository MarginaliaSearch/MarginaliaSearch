# Run

This directory is a staging area for running the system.  It contains scripts
and templates for installing the system on a server, and for running it locally.

## Requirements

**x86-64 Linux** - The system is only tested on x86-64 Linux.  It may work on other
platforms, but for lack of suitable hardware, this can not be guaranteed.

**Docker** - It is a bit of a pain to install, but if you follow
[this guide](https://docs.docker.com/engine/install/ubuntu/#install-using-the-repository) you're on the right track for ubuntu-like systems.

**JDK 24** - The code uses Java 24 preview features. 

**liburing** - The index uses `io_uring` and this is needed to access it.

The civilized way of installing this is to use [SDKMAN](https://sdkman.io/);
graalce is a good distribution choice but it doesn't matter too much.

## Quick Set up

[https://docs.marginalia.nu/](https://docs.marginalia.nu/) has a more comprehensive guide for the install
and operation of the search engine.  This is a quick guide for the impatient.

---

To go from a clean check out of the git repo to a running search engine,
follow these steps. 

You're assumed to sit in the project root the whole time.

### 1. Run the one-time setup

It will create the basic runtime directory structure and download some models and 
data that doesn't come with the git repo because git deals poorly with large binary files.

```shell
$ run/setup.sh
```

### 2. Compile the project and build docker images

```shell
$ ./gradlew docker
```
### 3.  Install the system

```shell
$ run/install.sh <install-directory>
```

To install the system, you need to run the install script.  It will prompt 
you for which installation mode you want to use.  The options are:

1. Barebones - This will install a white-label search engine with no data.  You can 
   use this to index your own data.  It disables and hides functionality that is strongly
   related to the Marginalia project, such as the Marginalia GUI. 
2. Full Marginalia Search instance - This will install an instance of the search engine
   configured like [search.marginalia.nu](https://search.marginalia.nu).  This is useful
   for local development and testing.
3. Non-docker installation - This will install the system outside of docker. 
   This is still an experimental run-mode.

It will also prompt you for account details for a new mariadb instance, which will be
created for you.  The database will be initialized with the schema and data required
for the search engine to run.

After filling out all the details, the script will copy the installation files to the
specified directory.

### 4. Run the system

```shell
$ cd install_directory
$ docker-compose up -d 
# To see the logs: 
$ docker-compose logs -f
```

You can now access a search interface at `http://localhost:8080`, and the admin interface
at `http://localhost:8081/`.   

There is no data in the system yet.  To load data into the system,
see the guide at [https://docs.marginalia.nu/](https://docs.marginalia.nu/).
