# Crawl

## 1. Crawl Job Extractor

The [crawl-job-extractor-process](crawl-job-extractor-process/) creates a crawl job specification 
based on the content in the database.

## 2. Crawl Process

The [crawling-process](crawling-process/) fetches website contents and saves them
as compressed JSON models described in [crawling-model](crawling-model/).

## 3. Converting Process

The [converting-process](converting-process/) reads crawl data from the crawling step and 
processes them, extracting keywords and metadata and saves them as compressed JSON models 
described in [converting-model](converting-model/).

## 4. Loading Process

The [loading-process](loading-process/) reads the processed data and creates an index journal
and lexicon, and loads domains and addresses into the MariaDB-database.

## Overview 

Schematically the crawling and loading process looks like this:

```
    //====================\\
    || Compressed JSON:   ||  Specifications
    || ID, Domain, Urls[] ||  File
    || ID, Domain, Urls[] ||
    || ID, Domain, Urls[] ||
    ||      ...           ||
    \\====================//
          |
    +-----------+  
    |  CRAWLING |  Fetch each URL and 
    |    STEP   |  output to file
    +-----------+
          |
    //========================\\
    ||  Compressed JSON:      || Crawl
    ||  Status, HTML[], ...   || Files
    ||  Status, HTML[], ...   ||
    ||  Status, HTML[], ...   ||
    ||     ...                ||
    \\========================//
          |
    +------------+
    | CONVERTING |  Analyze HTML and 
    |    STEP    |  extract keywords 
    +------------+  features, links, URLs
          |
    //==================\\
    || Compressed JSON: ||  Processed
    ||  URLs[]          ||  Files
    ||  Domains[]       ||
    ||  Links[]         ||  
    ||  Keywords[]      ||
    ||    ...           ||
    ||  URLs[]          ||
    ||  Domains[]       ||
    ||  Links[]         ||    
    ||  Keywords[]      ||
    ||    ...           ||
    \\==================//
          |
    +------------+
    |  LOADING   | Insert URLs in DB
    |    STEP    | Insert keywords in Index
    +------------+    
    
```