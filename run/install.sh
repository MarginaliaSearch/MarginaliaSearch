#!/bin/bash

#
# This script will set up a Marginalia instance in a given directory.
# It will create a docker-compose.yml file, and a directory structure
# with the necessary files.  It will also create a MariaDB database
# in docker, and run the flyway migrations to set up the database.
#
# After the script is run, the instance can be started with
# $ docker-compose up -d
#
# The instance can be stopped with
# $ docker-compose down -v
#
# It is likely that you will want to edit the docker-compose.yml file
# to change the ports that the services are exposed on, and to change
# the volumes that are mounted.  The default configuration is provided
# a starting point.

set -e

if [ -z "${1}" ]; then
  echo "Usage: $0 <install directory>"
  exit 1
fi
if [ -e "${1}" ]; then
  echo "ERROR: Destination ${1} already exists, refusing to overwrite"
  exit 1
fi

INSTALL_DIR=$(realpath ${1})

echo "Would you like to set up a:"
echo
echo "1) barebones instance (1 node)"
echo "2) barebones instance (2 nodes)"
echo "3) full Marginalia Search instance?"
echo "4) non-docker install? (not recommended)"
echo

read -p "Enter 1, 2, 3, or 4: " INSTANCE_TYPE

## Validate
if [ "${INSTANCE_TYPE}" != "1" ] && [ "${INSTANCE_TYPE}" != "2" ] && [ "${INSTANCE_TYPE}" != "3" ] && [ "${INSTANCE_TYPE}" != "4" ]; then
  echo
  echo "ERROR: Invalid instance type, choose 1, 2 or 3"
  exit 1
fi

echo
echo "We're going to set up a Mariadb database in docker, please enter some details"

read -p "MariaDB user (e.g. marginalia): " MARIADB_USER
read -s -p "MariaDB password (e.g. hunter2, or leave blank to generate one): " MARIADB_PASSWORD
echo
if [ ! -z "${MARIADB_PASSWORD}" ]; then
  echo
  read -s -p "MariaDB password (again): " MARIADB_PASSWORD2
  echo
fi

export MARIADB_USER
export MARIADB_PASSWORD
if [ "${INSTANCE_TYPE}" == "4" ]; then
  export MARIADB_HOST="localhost"
else
  export MARIADB_HOST="mariadb"
fi

if [ "${MARIADB_PASSWORD}" != "${MARIADB_PASSWORD2}" ]; then
  echo "ERROR: Passwords do not match"
  exit 1
fi

echo
echo "Will install to ${INSTALL_DIR}"
read -p "Press enter to continue, or Ctrl-C to abort"

export INSTALL_DIR
export INSTANCE_TYPE
export MARIADB_USER
export MARIADB_PASSWORD
export MARIADB_HOST

bash $(dirname $0)/install-noninteractive.sh ${INSTALL_DIR}