#!/bin/bash

### Settings that are passed as environment variables:

# $INSTANCE_TYPE can be 1, 2, 3, or 4

# * 1: barebones interface (1 index node)
# * 2: barebones interface (2 index nodes)
# * 3: full Marginalia Search production-like instance
# * 4: non-docker install

INSTANCE_TYPE=${INSTANCE_TYPE:-4}

# $MARIADB_USER and $MARIADB_PASSWORD are used to set up the MariaDB database,
# by default we use the user 'marginalia' and a randomly generated strong password

MARIADB_USER=${MARIADB_USER:-marginalia}
MARIADB_PASSWORD=${MARIADB_PASSWORD:-$(tr -dc 'A-Za-z0-9' < /dev/urandom | head -c 32)}

###

## Check for envsubst
if ! command -v envsubst &> /dev/null
then
    echo "The envsubst command could not be found, please install it.  It is usually part of GNU gettext."
    exit
fi

## Move to the directory of the script
pushd $(dirname $0)


## Check for the install directory
INSTALL_DIR=$(realpath ${1})

if [ -z ${INSTALL_DIR} ]; then
  echo "Usage: $0 <install directory>"
  exit 1
fi

mkdir -p ${INSTALL_DIR}

echo "** Copying files to ${INSTALL_DIR}"

for dir in model data conf conf/properties env; do
  if [ ! -d ${dir} ]; then
    echo "ERROR: ${dir} does not exist"
    exit 1
  fi
  echo "Copying ${dir}/"
  mkdir -p ${INSTALL_DIR}/${dir}
  find  ${dir} -maxdepth 1 -type f -exec cp -v {} ${INSTALL_DIR}/{} \;
done

# for barebones, tell the control service to hide the marginalia app specific stuff
if [ "${INSTANCE_TYPE}" == "1" ]; then
  echo "control.hideMarginaliaApp=true" > ${INSTALL_DIR}/conf/properties/control-service.properties
elif [ "${INSTANCE_TYPE}" == "2" ]; then
  echo "control.hideMarginaliaApp=true" > ${INSTALL_DIR}/conf/properties/control-service.properties
elif [ "${INSTANCE_TYPE}" == "4" ]; then
  echo "control.hideMarginaliaApp=true" > ${INSTALL_DIR}/conf/properties/control-service.properties
  # (leading with a blank newline is important, as we cannot trust that the source file ends with a new-line)
  cat >>${INSTALL_DIR}/conf/properties/system.properties <<EOF

# Override zookeeper hosts for non-docker install here:
zookeeper-hosts=localhost:2181

# Override the storage root for non-docker install here:
storage.root=${INSTALL_DIR}/index-1
EOF
fi

echo "** Creating directories"
mkdir -p ${INSTALL_DIR}/logs
mkdir -p ${INSTALL_DIR}/db
mkdir -p ${INSTALL_DIR}/index-1/{work,index,backup,storage,uploads}
if [ "${INSTANCE_TYPE}" == "2" ] || [ "${INSTANCE_TYPE}" == "3" ]; then
  mkdir -p ${INSTALL_DIR}/index-2/{work,index,backup,storage,uploads}
fi

echo "** Updating settings files"

cp install/prometheus.yml ${INSTALL_DIR}/
envsubst < install/mariadb.env.template > ${INSTALL_DIR}/env/mariadb.env
envsubst < install/db.properties.template > ${INSTALL_DIR}/conf/db.properties

echo "** Creating docker-compose.yml"

## Hack to get around envstubst substituting these values, which we want to be verbatim
export uval="\$\$MARIADB_USER"
export pval="\$\$MARIADB_PASSWORD"

export INSTALL_DIR

if [ "${INSTANCE_TYPE}" == "1" ]; then
  envsubst < install/barebones-1/docker-compose.yml.template >${INSTALL_DIR}/docker-compose.yml
elif [ "${INSTANCE_TYPE}" == "2" ]; then
  envsubst < install/barebones-2/docker-compose.yml.template >${INSTALL_DIR}/docker-compose.yml
elif [ "${INSTANCE_TYPE}" == "3" ]; then
  envsubst < install/marginalia-prod-like/docker-compose.yml.template >${INSTALL_DIR}/docker-compose.yml
elif [ "${INSTANCE_TYPE}" == "4" ]; then
  envsubst < install/no-docker/docker-compose.yml.template >${INSTALL_DIR}/docker-compose.yml
  cp install/no-docker/README ${INSTALL_DIR}/README

  echo
  echo "====="
  cat ${INSTALL_DIR}/README
  echo
  echo "====="
  echo "To read this again, look in ${INSTALL_DIR}/README"
  echo
fi

popd