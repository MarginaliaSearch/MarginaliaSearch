# System Properties

These are JVM system properties used by each service.  These properties can either
be loaded from a file or passed in as command line arguments, using `$JAVA_OPTS`.

The system will look for a properties file in `conf/properties/system.properties`,
within the install dir, as specified by `$WMSA_HOME`.

A template is available in [../run/template/conf/properties/system.properties](../run/template/conf/properties/system.properties).
## Global

| flag        | values     | description                          |
|-------------|------------|--------------------------------------|
| blacklist.disable | boolean | Disables the IP blacklist            |
| flyway.disable | boolean | Disables automatic Flyway migrations |

## Crawler Properties

| flag                        | values     | description                                                                              |
|-----------------------------|------------|------------------------------------------------------------------------------------------|
| crawler.userAgentString     | string | Sets the user agent string used by the crawler                                           |
| crawler.userAgentIdentifier | string | Sets the user agent identifier used by the crawler, e.g. what it looks for in robots.txt |
| crawler.poolSize            | integer | Sets the number of threads used by the crawler, more is faster, but uses more RAM |
| ip-blocklist.disabled       | boolean | Disables the IP blocklist |

## Converter Properties

| flag                        | values     | description                                                                                                                                              |
|-----------------------------|------------|----------------------------------------------------------------------------------------------------------------------------------------------------------|
| converter.sideloadThreshold | integer | Threshold value, in number of documents per domain, where a simpler processing method is used which uses less RAM.  10,000 is a good value for ~32GB RAM |

# Marginalia Application Specific

| flag                      | values     | description                                                   |
|---------------------------|------------|---------------------------------------------------------------|
| search.websiteUrl         | string | Overrides the website URL used in rendering                   |
| control.hideMarginaliaApp | boolean | Hides the Marginalia application from the control GUI results |
