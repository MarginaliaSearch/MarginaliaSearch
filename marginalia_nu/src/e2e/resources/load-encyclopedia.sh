#!/bin/bash

mkdir -p /var/lib/wmsa/conf/
mkdir -p /var/lib/wmsa/data/
mkdir -p /data

cat > /var/lib/wmsa/conf/db.properties <<EOF
  db.user=wmsa
  db.pass=wmsa
  db.conn=jdbc:mariadb://mariadb:3306/WMSA_prod?rewriteBatchedStatements=true
EOF

cat > /var/lib/wmsa/conf/hosts <<EOF
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

java -cp WMSA.jar nu.marginalia.wmsa.edge.tools.EncyclopediaLoaderTool data/wikipedia_en_100_nopic.zim


echo "ALL DONE"