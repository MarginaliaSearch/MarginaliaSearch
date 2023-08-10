# Crawling

This document is a draft.

## WARNING
Please don't run the crawler unless you intend to actually operate a public
facing search engine!  Use crawl sets from downloads.marginalia.nu instead.

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

Ensure that the system is running and go to https://localhost:8081.  See the documentation in [run/](../run/) for more information.
By default the system is configured to store data in `run/samples`.  (!!!FIXME: How do you change this now?!!!)


### Specifications

A crawl specification file is a compressed JSON file with each domain name to crawl, as well as
known URLs for each domain.  These are created in the `storage -> specifications` view in the operator's gui.

To bootstrap the system, you need a list of known domains.  This is just a text file with one domain name per line,
with blanklines and comments starting with `#` ignored.

Make it available over HTTP(S) and select `Download a list of domains from a URL` in the `Create New Specification`
form.  Make sure to give this specification a good description, as it will follow you around for  a while.

## Crawling

Refresh the specification list in the operator's gui.  You should see your new specification in the list.
Click the `[Info]` link next to it and select `[Crawl]` under `Actions`.

Depending on the size of the specification, this may take anywhere between a few minutes to a few weeks. 
You can follow the progress in the `Actors` view.

## Converting

Once the crawl is finished, you can convert the data to a format that can be loaded into the database.
This is done by going to the `storage -> crawl` view in the operator's gui, clicking the `[Info]` link
and pressing `[Convert]` under `Actions`.

The rest of the process should be automatic.  Follow the progress in the `Actors` view; the actor
`RECONVERT_LOAD` drives the process.  The process can be stopped by terminating this actor.  Depending on the
state, it may be necessary to restart from the beginning.  