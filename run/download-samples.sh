#!/bin/bash

set -e

# Check if wget exists
if command -v wget &> /dev/null; then
  dl_prg="wget -O"
elif command -v curl &> /dev/null; then
  dl_prg="curl -o"
else
  echo "Neither wget nor curl found, exiting .."
  exit 1
fi

case "$1" in
"s"|"m"|"l"|"xl")
    ;;
*)
    echo "Invalid argument. Must be one of 's', 'm', 'l' or 'xl'."
    exit 1
    ;;
esac

SAMPLE_NAME=crawl-${1:-m}
SAMPLE_DIR="node-1/samples/${SAMPLE_NAME}/"

function download_model {
  model=$1
  url=$2

  if [ ! -f $model ]; then
    echo "** Downloading $url"
    $dl_prg $model $url
  fi
}

pushd $(dirname $0)

if [ -d ${SAMPLE_DIR} ]; then
    echo "${SAMPLE_DIR} already exists; remove it if you want to re-download the sample"
fi

mkdir -p node-1/samples/
SAMPLE_TARBALL=samples/${SAMPLE_NAME}.tar.gz
download_model ${SAMPLE_TARBALL}.tmp https://downloads.marginalia.nu/${SAMPLE_TARBALL} && mv ${SAMPLE_TARBALL}.tmp ${SAMPLE_TARBALL}

if [ ! -f ${SAMPLE_TARBALL} ]; then
  echo "!! Failed"
  exit 255
fi

mkdir -p ${SAMPLE_DIR}
tar zxf ${SAMPLE_TARBALL} --strip-components=1 -C ${SAMPLE_DIR}

cat > "${SAMPLE_DIR}/marginalia-manifest.json" <<EOF
{ "description": "Sample data set ${SAMPLE_NAME}", "type": "CRAWL_DATA" }
EOF

popd
