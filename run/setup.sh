#!/bin/bash

## This script will perform a first-time setup of the run/ directory, as well as
# download third party language models and other files that aren't suitable for
# git

## The script can also be used to update the models and data files in an existing
# install directory. To do so, pass the path to the install directory as the first
# argument to the script. The script will check for the presence of conf/, model/
# and data/ directories in the install directory and update the files in them.

set -e

function download_model {
  model=$1
  url=$2
  md5sum=$3

  if [ ! -z $md5sum ]; then
    if [ -f $model ]; then
      echo "?? Checking $model checksum"
      if [ $(md5sum $model | cut -d ' ' -f 1) == $md5sum ]; then
        echo "** $model already exists and has correct checksum, skipping download"
        return
      else
        echo "** $model has incorrect checksum, redownloading"
        rm $model
      fi
    fi
  fi

  if [ ! -f $model ]; then
    echo "** $model absent, downloading $url"
    curl -s -o $model.tmp $url
    mv $model.tmp $model
  fi
}

if [ ! -z $1 ]; then
  echo "Install dir is $1"
  echo "?? Checking for conf/, model/ and data/ directories in $1"
  if [ ! -d $1/conf ]; then
    echo "** $1/conf/ not found, aborting"
    exit 255
  fi
  if [ ! -d $1/model ]; then
    echo "** $1/model/ not found, aborting"
    exit 255
  fi
  if [ ! -d $1/data ]; then
    echo "** $1/data/ not found, aborting"
    exit 255
  fi

  echo "** All directories found, proceeding with update in $1"
  pushd $1
else
  echo "No install dir specified, using current directory to set up run/"
  pushd $(dirname $0)
  if [ ! -d conf ]; then
    cp -r template/conf .
  fi
  mkdir -p model logs db  install  data samples
  mkdir -p {node-1,node-2}/{work,index,backup,samples/export,uploads}
fi

download_model model/English.DICT https://raw.githubusercontent.com/datquocnguyen/RDRPOSTagger/master/Models/POS/English.DICT
download_model model/English.RDR https://raw.githubusercontent.com/datquocnguyen/RDRPOSTagger/master/Models/POS/English.RDR
download_model model/opennlp-sentence.bin https://downloads.apache.org/opennlp/models/ud-models-1.0/opennlp-en-ud-ewt-sentence-1.0-1.9.3.bin
download_model model/opennlp-tokens.bin https://downloads.apache.org/opennlp/models/ud-models-1.0/opennlp-en-ud-ewt-tokens-1.0-1.9.3.bin
download_model model/segments.bin https://downloads.marginalia.nu/model/segments.bin a2650796c77968b1bd9db0d7c01e3150
download_model model/tfreq-new-algo3.bin https://downloads.marginalia.nu/model/tfreq-new-algo3.bin a38f0809f983723001dfc784d88ebb6d
download_model model/lid.176.ftz https://downloads.marginalia.nu/model/lid.176.ftz 340156704bb8c8e50c4abf35a7ec2569

download_model data/IP2LOCATION-LITE-DB1.CSV.ZIP https://download.ip2location.com/lite/IP2LOCATION-LITE-DB1.CSV.ZIP
unzip -qn -d data data/IP2LOCATION-LITE-DB1.CSV.ZIP

download_model data/asn-data-raw-table https://thyme.apnic.net/current/data-raw-table
download_model data/asn-used-autnums https://thyme.apnic.net/current/data-used-autnums

download_model data/adblock.txt https://downloads.marginalia.nu/data/adblock.txt
if [ ! -f data/suggestions.txt ]; then
  download_model data/suggestions.txt.gz https://downloads.marginalia.nu/data/suggestions.txt.gz
  gunzip data/suggestions.txt.gz
fi

popd
