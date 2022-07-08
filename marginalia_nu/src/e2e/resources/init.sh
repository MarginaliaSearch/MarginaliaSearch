#!/bin/bash

HOME=/wmsa

mkdir -p ${HOME}/encyclopedia
mkdir -p ${HOME}/conf
mkdir -p ${HOME}/index/write
mkdir -p ${HOME}/index/read
mkdir -p ${HOME}/tmp-slow
mkdir -p ${HOME}/tmp-fast

cat > ${HOME}/suggestions.txt <<EOF
state
three
while
used
university
can
united
under
known
season
many
year
EOF

cat > ${HOME}/conf/disks.properties <<EOF
encyclopedia=${HOME}/encyclopedia

index-write=${HOME}/index/write
index-read=${HOME}/index/read
tmp-slow=${HOME}/tmp-slow
tmp-fast=${HOME}/tmp-fast
EOF

cat > ${HOME}/conf/db.properties <<EOF
  db.user=wmsa
  db.pass=wmsa
  db.conn=jdbc:mariadb://mariadb:3306/WMSA_prod?rewriteBatchedStatements=true
EOF

cat > ${HOME}/conf/ranking-settings.yaml <<EOF
---
retro:
  - "%"
small:
  - "%"
academia:
  - "%edu"
  - "%ac.jp"
  - "%ac.uk"
standard:
  - "%"
EOF

cat > ${HOME}/conf/hosts <<EOF
# service-name host-name
resource-store resource-store
renderer renderer
auth auth
api api
smhi-scraper smhi-scraper
podcast-scraper podcast-scraper
edge-index edge-index
edge-search edge-search
encyclopedia encyclopedia
edge-assistant edge-assistant
memex memex
dating dating
EOF

echo "*** Starting $1"
WMSA_HOME=${HOME} java -Dsmall-ram=TRUE -Dservice-host=0.0.0.0 -jar /WMSA.jar start $1