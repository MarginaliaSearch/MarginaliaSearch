#!/bin/bash

set -e

function download_model {
  model=$1
  url=$2

  if [ ! -f $model ]; then
    echo "** Downloading $url"
    wget -O $model $url
  fi
}

pushd $(dirname $0)

cp -r template/conf .
mkdir -p model logs db samples install vol/ir vol/iw/search-sets

download_model model/English.DICT https://raw.githubusercontent.com/datquocnguyen/RDRPOSTagger/master/Models/POS/English.DICT
download_model model/English.RDR https://raw.githubusercontent.com/datquocnguyen/RDRPOSTagger/master/Models/POS/English.RDR
download_model model/opennlp-sentence.bin https://mirrors.estointernet.in/apache/opennlp/models/ud-models-1.0/opennlp-en-ud-ewt-sentence-1.0-1.9.3.bin
download_model model/opennlp-tokens.bin https://mirrors.estointernet.in/apache/opennlp/models/ud-models-1.0/opennlp-en-ud-ewt-tokens-1.0-1.9.3.bin
download_model model/IP2LOCATION-LITE-DB1.CSV.ZIP https://download.ip2location.com/lite/IP2LOCATION-LITE-DB1.CSV.ZIP
download_model model/ngrams.bin https://downloads.marginalia.nu/model/ngrams.bin
download_model model/tfreq-new-algo3.bin https://downloads.marginalia.nu/model/tfreq-new-algo3.bin

popd
