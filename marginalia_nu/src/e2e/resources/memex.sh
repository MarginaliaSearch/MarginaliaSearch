#!/bin/bash

HOME=/wmsa

mkdir -p ${HOME}/conf

cat > ${HOME}/conf/db.properties <<EOF
  db.user=wmsa
  db.pass=wmsa
  db.conn=jdbc:mariadb://mariadb:3306/WMSA_prod?rewriteBatchedStatements=true
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

mkdir -p /memex /gmi /html

echo "*** Starting $1"
WMSA_HOME=${HOME} java \
    -Dmemex-root=/memex\
    -Dmemex-html-resources=/html\
    -Dmemex-gmi-resources=/gmi\
    -Dmemex-disable-git=TRUE\
    -Dmemex-disable-gemini=TRUE\
    -Dservice-host=0.0.0.0\
    -jar /WMSA.jar start $1