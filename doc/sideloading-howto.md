# Sideloading How-To

(This document is a bit of a draft to get this down in writing
while it's still fresh in my head.)

Some websites are much larger than others, this includes
Wikipedia, Stack Overflow, and a few others.  They are so
large they are impractical to crawl in the traditional fashion,
but luckily they make available data dumps that can be processed
and loaded into the search engine through other means.

## Sideloading a directory tree

For relatively small websites, ad-hoc side-loading is available directly from a
folder structure on the hard drive. This is intended for loading manuals, 
documentation and similar data sets that are large and slowly changing.

A website can be archived with wget, like this

```bash
UA="search.marginalia.nu" \
DOMAIN="www.example.com" \
wget -nc -x --continue -w 1 -r -U ${UA} -A "html" ${DOMAIN}
```

After doing this to a bunch of websites, create a YAML file something like this:

```yaml
sources:
- name: jdk-20
  dir: "jdk-20/"
  domainName: "docs.oracle.com"
  baseUrl: "https://docs.oracle.com/en/java/javase/20/docs"
  keywords:
  - "java"
  - "docs"
  - "documentation"
  - "javadoc"
- name: python3
  dir: "python-3.11.5/"
  domainName: "docs.python.org"
  baseUrl: "https://docs.python.org/3/"
  keywords:
  - "python"
  - "docs"
  - "documentation"
- name: mariadb.com
  dir: "mariadb.com/"
  domainName: "mariadb.com"
  baseUrl: "https://mariadb.com/"
  keywords:
  - "sql"
  - "docs"
  - "mariadb"
  - "mysql"
```

|parameter|description|
|----|----|
|name|Purely informative|
|dir|Path of website contents relative to the location of the yaml file|
|domainName|The domain name of the website|
|baseUrl|This URL will be prefixed to the contents of `dir`|
|keywords|These supplemental keywords will be injected in each document|

The directory structure corresponding to the above might look like

```
docs-index.yaml
jdk-20/
jdk-20/resources/
jdk-20/api/
jdk-20/api/[...]
jdk-20/specs/
jdk-20/specs/[...]
jdk-20/index.html
mariadb.com
mariadb.com/kb/
mariadb.com/kb/[...]
python-3.11.5
python-3.11.5/genindex-B.html
python-3.11.5/library/
python-3.11.5/distutils/
python-3.11.5/[...]
[...]
```

This yaml-file can be processed and loaded into the search engine through the
Actions view.

## Sideloading Wikipedia

For now, this workflow depends on using the conversion process from
[https://encyclopedia.marginalia.nu/](https://encyclopedia.marginalia.nu/)
to pre-digest the data.  This is because it uses OpenZIM which has a
license that is incompatible with this project.

Build the [encyclopedia.marginalia.nu Code](https://github.com/MarginaliaSearch/encyclopedia.marginalia.nu)
and follow the instructions for downloading a ZIM file, and then run something like

```$./encyclopedia convert file.zim articles.db```

This db-file can be processed and loaded into the search engine through the
Actions view.

FIXME: It will currently only point to encyclopedia.marginalia.nu and not main Wikipedia,
this should be made configurable.

## Sideloading Stack Overflow/Stackexchange

Stackexchange makes dumps available on Archive.org.  These are unfortunately on a format that 
needs some heavy-handed pre-processing before they can be loaded.  A tool is available for 
this in [tools/stackexchange-converter](../code/tools/stackexchange-converter).

After running `gradlew dist`, this tool is found in `build/dist/stackexchange-converter`,
follow the instructions in the stackexchange-converter readme, and
convert the stackexchange xml.7z-files to sqlite db-files. 

A directory with such db-files can be processed and loaded into the 
search engine through the Actions view.