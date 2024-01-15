# Crawling

This document is a draft.

## WARNING

Please don't run the crawler unless you intend to actually operate a public
facing search engine!  For testing, use crawl sets from [downloads.marginalia.nu](https://downloads.marginalia.nu/) instead;
or if you wish to play with the crawler, crawl a small set of domains from people who are
ok with it, use your own, your friends, or any subdomain from marginalia.nu.

See the documentation in run/ for more information on how to load sample data! 

Reckless crawling annoys webmasters and makes it harder to run an independent search engine. 
Crawling from a domestic IP address is also likely to put you on a greylist
of probable bots.  You will solve CAPTCHAs for almost every website you visit
for weeks, and may be permanently blocked from a few IPs.

## Prerequisites

You probably want to run a local bind resolver to speed up DNS lookups and reduce the amount of
DNS traffic. 

These processes require a lot of disk space.  It's strongly recommended to use a dedicated disk for
the index storage subdirectory, it doesn't need to be extremely fast, but it should be a few terabytes in size.  

It should be mounted with `noatime`.  It may be a good idea to format the disk with a block size of 4096 bytes.  This will reduce the amount of disk space used by the crawler.

Make sure you configure the user-agent properly.  This will be used to identify the crawler,
and is matched against the robots.txt file.  The crawler will not crawl sites that don't allow it.
See [wiki://Robots_exclusion_standard](https://en.wikipedia.org/wiki/Robots_exclusion_standard) for more information
about robots.txt; the user agent can be configured in conf/properties/system.properties; see the 
[system-properties](system-properties.md) documentation for more information.

## Setup

Ensure that the system is running and go to https://localhost:8081.  

With the default test configuration, the system is configured to 
store data in `node-1/storage`.

## Fresh Crawl

While a running search engine can use the link database to figure out which websites to visit, a clean
system does not know of any links.  To bootstrap a crawl, a crawl specification needs to be created to 
seed the domain database.

Go to `Nodes->Node 1->Actions->New Crawl`

![img](images/new_crawl.png)

Click the link that says 'New Spec' to arrive at a form for creating a new specification:

Fill out the form with a description and a link to the domain list. 

## Crawling 

If you aren't redirected there automatically, go back to the `New Crawl` page under Node 1 -> Actions. 
Your new specification should now be listed.  

Check the box next to it, and click `[Trigger New Crawl]`.

![img](images/new_crawl2.png)

This will start the crawling process.  Crawling may take a while, depending on the size
of the domain list and the size of the websites.  

![img](images/crawl_in_progress.png)

Eventually a process bar will show up, and the crawl will start.  When it reaches 100%, the crawl is done.
You can also monitor the `Events Summary` table on the same page to see what happened after the fact.

It is expected that the crawl will stall out toward the end  of the process, this is a statistical effect since
the largest websites take the longest to finish, and tend to be the ones lingering at 99% or so completion.  The
crawler has a timeout of 5 hours, where if no new domains are finished crawling, it will stop, to prevent crawler traps
from stalling the crawl indefinitely. 

**Be sure to read the section on re-crawling!**

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
it is preferable to do a recrawl.  This will try to reduce the amount of data that needs to be fetched.

To trigger a Recrawl, ensure your crawl data is set to active, and then go to Actions -> Trigger Recrawl,
and click `[Trigger Recrawl]`.  This will behave much like the old crawling step.   Once done, it needs to be
processed like the old crawl data.
