# Crawling

This document is a first draft.

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

## Setup

To operate the crawler, you need two files.

### Specifications

A crawl specification file is a compressed JSON file with each domain name to crawl, as well as
known URLs for each domain.  These are created with the [crawl-job-extractor](../tools/crawl-job-extractor/)
tool.

### Crawl Plan

You also need a crawl plan. This is a YAML file that specifies where to store the crawl data. This
file is also used by the converter.

This is an example from production. Note that the crawl specification mentioned previously is pointed
to by the `jobSpec` key.

```yaml
jobSpec: "/var/lib/wmsa/archive/crawl-2023-06-07/2023-06-07.spec"
crawl:
  dir: "/var/lib/wmsa/archive/crawl-2023-06-07/"
  logName: "crawler.log"
process:
  dir: "/var/lib/wmsa/archive/processed-2023-06-07/"
  logName: "process.log"
```

## Crawling

Run the crawler-process script with the crawl plan as an argument.

In practice something like this:

```bash
screen sudo -u searchengine WMSA_HOME=/path/to/install/dir ./crawler-process crawl-plan.yaml
```

This proces will run for a long time, up to a week.  It will journal its progress in `crawler.log`,
and if the process should halt somehow, it replay the journal and continue where was.  Do give it a 
while before restarting though, to not annoy webmasters by re-crawling a bunch of websites.

The crawler will populate the crawl directory with a directory structure.  Note that on mechanical drives,
removing these files will take hours.  You probably want a separate hard drive for this as the filesystem
will get severely gunked up. 

## Converting

The converter process takes the same argument as the crawler process.  It will read the crawl data
and extract keywords and metadata and save them as compressed JSON models.  It will create another huge
directory structure in the process directory, and uses its own journal to keep track of progress.

```bash
screen sudo -u searchengine WMSA_HOME=/path/to/install/dir ./converter-process crawl-plan.yaml
```

**Note:** This process will use *a lot* of CPU.  Expect every available core to be at 100% for several days.

## Loader

The loader process takes the same argument as the crawler and converter processes.  It will read converted
data and insert it into the database and create a lexicon and index journal.

**Note:** It will wipe the URL database before inserting data.  It is a good idea to 
bring the entire search-engine offline while this is happening.  The loader will run
for a day or so. 