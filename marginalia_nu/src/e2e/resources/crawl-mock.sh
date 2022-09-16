#!/bin/bash

mkdir -p /var/lib/wmsa/conf/
mkdir -p /var/lib/wmsa/data/

echo "search.marginalia.nu" > /var/lib/wmsa/conf/user-agent

cat crawl/crawl.plan
cat << EOF
  ####   #####     ##    #    #  #
 #    #  #    #   #  #   #    #  #
 #       #    #  #    #  #    #  #
 #       #####   ######  # ## #  #
 #    #  #   #   #    #  ##  ##  #
  ####   #    #  #    #  #    #  ######
EOF
java -jar WMSA.jar crawl crawl/crawl.plan

echo "ALL DONE"