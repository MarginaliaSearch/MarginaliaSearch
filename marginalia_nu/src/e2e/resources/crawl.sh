#!/bin/bash

mkdir -p /var/lib/wmsa/conf/
mkdir -p /var/lib/wmsa/data/

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
edge-archive edge-archive
edge-assistant edge-assistant
memex memex
dating dating
EOF


cat crawl/crawl.plan
cat << EOF
  ####   #####     ##    #    #  #
 #    #  #    #   #  #   #    #  #
 #       #    #  #    #  #    #  #
 #       #####   ######  # ## #  #
 #    #  #   #   #    #  ##  ##  #
  ####   #    #  #    #  #    #  ######
EOF
java -DdefaultCrawlDelay=1 -cp WMSA.jar nu.marginalia.wmsa.edge.crawling.CrawlerMain crawl/crawl.plan

cat <<EOF

  ####    ####   #    #  #    #  ######  #####    #####
 #    #  #    #  ##   #  #    #  #       #    #     #
 #       #    #  # #  #  #    #  #####   #    #     #
 #       #    #  #  # #  #    #  #       #####      #
 #    #  #    #  #   ##   #  #   #       #   #      #
  ####    ####   #    #    ##    ######  #    #     #

EOF
java -cp WMSA.jar nu.marginalia.wmsa.edge.converting.ConverterMain crawl/crawl.plan
cat <<EOF

 #        ####     ##    #####
 #       #    #   #  #   #    #
 #       #    #  #    #  #    #
 #       #    #  ######  #    #
 #       #    #  #    #  #    #
 ######   ####   #    #  #####

EOF
java -Dkeyword-index=0 -cp WMSA.jar nu.marginalia.wmsa.edge.converting.LoaderMain crawl/crawl.plan

chmod -R 777 crawl/

cat <<EOF

  #####  #####      #     ####    ####   ######  #####
    #    #    #     #    #    #  #    #  #       #    #
    #    #    #     #    #       #       #####   #    #
    #    #####      #    #  ###  #  ###  #       #####
    #    #   #      #    #    #  #    #  #       #   #
    #    #    #     #     ####    ####   ######  #    #

EOF
java -cp WMSA.jar nu.marginalia.wmsa.edge.converting.ReindexTriggerMain edge-index

echo "ALL DONE"