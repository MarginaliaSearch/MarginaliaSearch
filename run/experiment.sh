#!/bin/bash

set -e

EXPERIMENT=$1
SAMPLE_NAME=crawl-${2:-m}
ARGS=${@:3}
SAMPLE_DIR="samples/${SAMPLE_NAME}/"

echo "args = $ARGS"

## Configuration

JAVA_OPTS="
-Dcrawl.rootDirRewrite=/crawl:${SAMPLE_DIR}
-Ddb.overrideJdbc=jdbc:mariadb://localhost:3306/WMSA_prod?rewriteBatchedStatements=true
"

## Configuration ends

if [ -z "$EXPERIMENT" ]; then
  echo "Usage: $0 experiment-name"
  exit 255;
fi

function download_model {
  model=$1
  url=$2

  if [ ! -f $model ]; then
    echo "** Downloading $url"
    wget -O $model $url
  fi
}

pushd $(dirname $0)

## Upgrade the tools

rm -rf install/*
tar xf ../code/tools/experiment-runner/build/distributions/experiment-runner.tar -C install/

## Download the sample if necessary

if [ ! -d ${SAMPLE_DIR} ]; then
  mkdir -p samples/

  SAMPLE_TARBALL=samples/${SAMPLE_NAME}.tar.gz
  download_model ${SAMPLE_TARBALL} https://downloads.marginalia.nu/${SAMPLE_TARBALL} || rm ${SAMPLE_TARBALL}

  if [ ! -f ${SAMPLE_TARBALL} ]; then
    echo "!! Failed"
    exit 255
  fi

  mkdir -p samples/${SAMPLE_NAME}
  if [ ! -f $SAMPLE_DIR/plan.yaml ]; then
    echo "Uncompressing"
    tar zxf ${SAMPLE_TARBALL} --strip-components=1 -C ${SAMPLE_DIR}
  fi
fi

## Wipe the old index data

PATH+=":install/experiment-runner/bin"

export WMSA_HOME=.
export PATH

export JAVA_OPTS

experiment-runner ${SAMPLE_DIR}/plan.yaml ${EXPERIMENT} ${ARGS}

popd
