#!/bin/bash

set -e

SAMPLE_NAME=crawl-${1:-m}
SAMPLE_DIR="samples/${SAMPLE_NAME}/"

## Configuration

CONVERTER_PROCESS_OPTS="
-Xmx16G
-XX:-CompactStrings
-XX:+UseParallelGC
-XX:GCTimeRatio=14
-XX:ParallelGCThreads=15
"

LOADER_PROCESS_OPTS="
-Dsmall-ram=TRUE
-Dlocal-index-path=vol/iw
"

JAVA_OPTS="
-Dcrawl.rootDirRewrite=/crawl:${SAMPLE_DIR}
-Ddb.overrideJdbc=jdbc:mariadb://localhost:3306/WMSA_prod?rewriteBatchedStatements=true
"

## Configuration ends

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

rm -rf install/loader-process install/converter-process
tar xf ../crawl/loading-process/build/distributions/loader-process.tar -C install/
tar xf ../crawl/converting-process/build/distributions/converter-process.tar -C install/

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

rm -f ${SAMPLE_DIR}/process/process.log
rm -f vol/iw/dictionary.dat
rm -f vol/iw/index.dat

PATH+=":install/converter-process/bin"
PATH+=":install/loader-process/bin"

export WMSA_HOME=.
export PATH

export JAVA_OPTS
export CONVERTER_PROCESS_OPTS
export LOADER_PROCESS_OPTS

converter-process ${SAMPLE_DIR}/plan.yaml
loader-process ${SAMPLE_DIR}/plan.yaml

mv vol/iw/index.dat vol/iw/0/page-index.dat
rm -f vol/ir/0/*

popd
