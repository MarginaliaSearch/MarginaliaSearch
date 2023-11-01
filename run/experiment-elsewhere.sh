#!/bin/bash

set -e

EXPERIMENT=$1
SAMPLE_NAME=crawl-${2:-m}
ARGS=${@:3}
SAMPLE_DIR="node-1/samples/${SAMPLE_NAME}/"

export EXPERIMENT_RUNNER_OPTS="--enable-preview"
echo "args = $ARGS"

## Configuration

pushd $(dirname $0)

JAVA_OPTS="
-Dcrawl.rootDirRewrite=/crawl:${SAMPLE_DIR}
-Ddb.overrideJdbc=jdbc:mariadb://localhost:3306/WMSA_prod?rewriteBatchedStatements=true
-ea
"

## Configuration ends

if [ -z "$EXPERIMENT" ]; then
  echo "Usage: $0 experiment-name path-to-crawl-data"
  exit 255;
fi

tar xf ../code/tools/experiment-runner/build/distributions/experiment-runner.tar -C install/

PATH+=":install/experiment-runner/bin"

export WMSA_HOME=.
export PATH
export JAVA_OPTS

experiment-runner $2 ${EXPERIMENT} ${ARGS}

popd
