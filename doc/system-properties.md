# System Properties

These are JVM system properties used by each service

## Search Service
| flag        | values     | description                                           |
|-------------|------------|-------------------------------------------------------|
| website-url |https://search.marginalia.nu/|Overrides the website URL used in rendering|

## Index Service

|flag| values     | description                                                   |
|---|------------|---------------------------------------------------------------|
|lexiconSizeHint| 1000000000 | The default size of the lexicon, speeds up start time in prod |

## Crawler Process
|flag| values     | description                                           |
|---|------------|-------------------------------------------------------|
|crawl.rootDirRewrite|/some/path|Sets the base directory of a crawl plan |

## Converter Process
|flag| values     | description                                           |
|---|------------|-------------------------------------------------------|
|crawl.rootDirRewrite|/some/path|Sets the base directory of a crawl plan |

## Loader Process
|flag| values     | description                                           |
|---|------------|-------------------------------------------------------|
|lexiconSizeHint| 800000000  | The default size of the lexicon                       |
|local-index-path| /some/path | Selects the location the loader will write index data |
|crawl.rootDirRewrite|/some/path|Sets the base directory of a crawl plan |
