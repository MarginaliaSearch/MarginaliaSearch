# Crawl Job Extractor

The crawl job extractor creates a file containing a list of domains
along with known URLs. 

This is consumed by [processes/crawling-process](../../processes/crawling-process).

## Usage


The crawl job extractor has three modes of operation:

```
# 1  grab domains from the database
./crawl-job-extractor file.out

# 2  grab domains from a file
./crawl-job-extractor file.out -f domains.txt

# 3  grab domains from the command line
./crawl-job-extractor file.out domain1 domain2 ...
```

* When only a single argument is passed, the file name to write to, it will create a complete list of domains
  and URLs known to the system from the list of already indexed domains, 
  as well as domains from the CRAWL_QUEUE table in the database.
* When the command line is passed like `./crawl-job-extractor output-file -f domains.txt`,
  domains will be read from non-blank and non-comment lines in the file.
* In other cases, the 2nd argument onward to the command will be interpreted as domain-names.

In the last two modes, if the crawl-job-extractor is able to connect to the database, it will use
information from the link database to populate the list of URLs for each domain, otherwise it will
create a spec with only the domain name and the index address, so the crawler will have to figure out
the rest. 

The crawl-specification is zstd-compressed json.

## Tricks

### Joining two specifications

Two or more specifications can be joined with a shell command on the form

```shell
$ zstdcat file1 file2 | zstd -o new-file
```

### Inspection

The file can also be inspected with `zstdless`, 
or combinations like `zstdcat file | jq`