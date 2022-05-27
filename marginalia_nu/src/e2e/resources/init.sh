#!/bin/bash

mkdir -p /var/lib/wmsa/encyclopedia
mkdir -p /var/lib/wmsa/conf
mkdir -p /var/lib/wmsa/index/write
mkdir -p /var/lib/wmsa/index/read
mkdir -p /backup/work/index-tmp

mkdir -p /var/log/wmsa
cat > /var/lib/wmsa/suggestions.txt <<EOF
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

cat > /var/lib/wmsa/conf/disks.properties <<EOF
encyclopedia=/var/lib/wmsa/encyclopedia
EOF

cat > /var/lib/wmsa/conf/db.properties <<EOF
  db.user=wmsa
  db.pass=wmsa
  db.conn=jdbc:mariadb://mariadb:3306/WMSA_prod?rewriteBatchedStatements=true
EOF

cat > /var/lib/wmsa/conf/ranking-settings.yaml <<EOF
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

java -Dsmall-ram=TRUE -Dservice-host=0.0.0.0 -jar /WMSA.jar start $1