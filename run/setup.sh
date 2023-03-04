#!/bin/bash

set -e

pushd $(dirname $0)

cp -r template/conf .
mkdir -p model logs db samples


popd
