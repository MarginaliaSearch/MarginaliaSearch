#!/bin/bash
# Compatibility shim with the linux deployment used in production which *will* not link to liburing using
# any sort of sane compiler or pkg-config flags

set -e

URING_PATH=$(pkg-config liburing --keep-system-libs --libs-only-L | cut -c 3- | tr -d \ )
if [ ! -z "${URING_PATH}" ]; then
  echo ${URING_PATH}/liburing.so
fi