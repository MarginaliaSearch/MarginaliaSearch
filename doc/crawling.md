# Crawling

This document is a draft.

## WARNING
Please don't run the crawler unless you intend to actually operate a public
facing search engine!  For testing, use crawl sets from downloads.marginalia.nu instead.

See the documentation in run/ for more information.

Reckless crawling annoys webmasters and makes it harder to run an independent search engine. 
Crawling from a domestic IP address is also likely to put you on a greylist
of probable bots.  You will solve CAPTCHAs for almost every website you visit
for weeks.

## Prerequisites

You probably want to run a local bind resolver to speed up DNS lookups and reduce the amount of
DNS traffic. 

These processes require a lot of disk space.  It's strongly recommended to use a dedicated disk,
it doesn't need to be extremely fast, but it should be a few terabytes in size.  It should be mounted
with `noatime` and partitioned with a large block size.  It may be a good idea to format the disk with 
a block size of 4096 bytes.  This will reduce the amount of disk space used by the crawler.

Make sure you configure the user-agent properly.  This will be used to identify the crawler,
and is matched against the robots.txt file.  The crawler will not crawl sites that don't allow it.

This can be done by editing the file `${WMSA_HOME}/conf/user-agent`.

## Setup

Ensure that the system is running and go to https://localhost:8081.  

By default the system is configured to store data in `run/node-1/samples`. 


### Specifications

While a running search engine can use the link database to figure out which websites to visit, a clean
system does not know of any links.  To bootstrap a crawl, a crawl specification can be created.  

You need a list of known domains.  This is just a text file with one domain name per line,
with blanklines and comments starting with `#` ignored.  Make it available over HTTP(S).

Go to

* System -> Nodes
* Select node 1
* Storage -> Specs
* Click `[Create New Specification]`

Fill out the form with a description and a link to the domain list. 

## Crawling 

Refresh the specification list in the operator's gui.  You should see your new specification in the list.
Click the link and select `[Crawl]` under `Actions`.

Depending on the size of the specification, this may take anywhere between a few minutes to a few weeks. 
You can follow the progress in the `Overview` view.  It's fairly common for the crawler to get stuck at 
99%, this is from the crawler finishing up the largest sites.  It will abort if no progress has been made
in five hours. 

You can manually also abort the crawler by going to

* System -> Nodes -> `[your node]` -> Actors.

Toggle both CRAWL and PROC_CRAWLER_SPAWNER to `[OFF]`.  

CRAWL controls the larger crawler process, and PROC_CRAWLER_SPAWNER spawns the actual
crawler process.  The crawler will be aborted, but the crawl data should be intact. 

At this point you'll want to set PROC_CRAWLER_SPAWNER back to `[ON]`, as the crawler
won't be able to start until it's set to this mode.

!!! FIXME: This UX kinda sucks, should be an abort button ideally, none of this having to toggle
circuit breakers on and off.

## Converting

Once the crawl is finished, you can convert and load the data to a format that can be loaded into the database.

First you'll want to go to Storage -> Crawl Data, and toggle the `State` field next to your new crawl
data into `Active`.  This will mark it as eligible for processing. 

Next, go to Actions -> Process Crawl Data, and click `[Trigger Reprocessing]`.  Ensure your crawl data
is visible in the list. This will start the automatic conversion and loading process, which can be followed
in the `Overview` view.

This process will take a while, and will run these discrete steps:

* CONVERT the crawl data into a format that can be loaded into the database
* LOAD, load posts into the mariadb database, construct an index journal and sqlite linkdb 
* Delete the processed data (optional; depending on node configuration)
* Create a backup of the index journal to be loaded (can be restored later)
* Repartition and create new domain rankings
* Construct a new index 
* * Forward
* * Full
* * Priority
* Switch to the new index

All of this is automatic and most of it is visible in the `Overview` view. 

## Recrawling (IMPORTANT)

The work flow with a crawl spec was a one-off process to bootstrap the search engine.  To keep the search engine up to date,
it is preferrable to do a recrawl.  This will try to reduce the amount of data that needs to be fetched.

To trigger a Recrawl, ensure your crawl data is set to active, and then go to Actions -> Trigger Recrawl,
and click `[Trigger Recrawl]`.  This will behave much like the old crawling step.   Once done, it needs to be
processed like the old crawl data.
