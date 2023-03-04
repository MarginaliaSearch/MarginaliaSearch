#!/bin/bash

set -e

## Configuration

SAMPLE_DIR="samples/crawl-l/"

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

pushd $(dirname $0)

## Wipe the old index data

rm -f ${SAMPLE_DIR}/process/process.log
rm -f vol/iw/dictionary.dat
rm -f vol/iw/index.dat

## Upgrade the tools

rm -rf install/loader-process install/converter-process
tar xf ../crawl/loading-process/build/distributions/loader-process.tar -C install/
tar xf ../crawl/converting-process/build/distributions/converter-process.tar -C install/

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

popd
