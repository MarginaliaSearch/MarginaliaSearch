@param String title
@param boolean allowIndexing = false

<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Marginalia Search Engine - ${title}</title>
    @if (!Boolean.getBoolean("test-env"))
        <link rel="stylesheet" href="/css/style.css" />
        <link rel="stylesheet" href="/css/fa-all.min.css" />
    @else
        @template.part.testenvHead()
    @endif

    @if (allowIndexing)
        <meta name="robots" content="index">

        <meta property="og:description" content="Marginalia Search is a small independent do-it-yourself search engine for surprising but content-rich websites that never ask you to accept cookies or subscribe to newsletters. The goal is to bring you the sort of grass fed, free range HTML your grandma used to write. " />
        <meta property="og:locale" content="en_US" />
        <meta property="og:site_name" content="Marginalia Search" />
        <meta property="og:type" content="website" />
        <meta property="og:url" content="https://marginalia-search.com/" />
    @else
        <meta name="robots" content="noindex">
    @endif

    <link rel="search" type="application/opensearchdescription+xml" href="/opensearch.xml" title="Marginalia">

</head>