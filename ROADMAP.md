# Roadmap

This is a roadmap with major features planned for Marginalia Search.

It's not set in any particular order and other features will definitely be implemented as well.

## Proper Position Index

Currently the search engine uses a fixed width bit mask to indicate word positions.  It has the benefit
of being very fast to evaluate, but is inaccurate and has the drawback of making support for quoted
search terms difficult and reliant on indexing word n-grams known beforehand.

The positions mask should be supplemented or replaced with a more accurate (e.g.) gamma coded positions
list, as is the civilized way of doing this.

## Hybridize crawler w/ Common Crawl data

Sometimes Marginalia's relatively obscure crawler is blocked when attempting to crawl a website, or for
other technical reasons, it may be difficult.  A possible work around is to hybridize the crawler so that
it attempts to fetch such websites from common crawl.  That said, retaining the ability to independently
crawl the web is still strongly desirable.

As a rough sketch, the crawler would identify target websites, consume CC's index, and then fetch the WARC data
with byte range queries.

## Some sort of Safe Search

The search engine has a bit of a problem showing spicy content mixed in with the results.  It would be desirable
to have a way to filter this out.  It's likely something like a URL blacklist (e.g. [UT1](https://dsi.ut-capitole.fr/blacklists/index_en.php) )
combined with naive bayesian filter would go a long way, or something more sophisticated,

## Additional Language Support

It would be desirable if the search engine supported more languages than English.  This is partially about
rooting out assumptions regarding character encoding, but there's most likely some amount of custom logic
associated with each language added, at least a models file or two, as well as some fine tuning.

It would be very helpful to find a speaker of a large language other than English to help in the fine tuning.

## Finalize RSS support

Marginalia has experimental RSS preview support for a few domains.  This works well and
it should be extended to all domains.  It would also be interesting to offer search of the
RSS data itself.

## Support for binary formats like PDF

Add support for formats like PDF.

The crawler needs to be modified to retain them, and the conversion logic needs to parse them.  
The documents database probably should have some sort of flag indicating it's a PDF as well.

PDF parsing is known to be a bit of a security libability so some thought needs to be put in
that direction as well.

## Custom ranking logic

Stract does an interesting thing where they have configurable search filters.

This looks like a good idea that wouldn't just help clean up the search filters on the main
website, but might be cheap enough we might go as far as to offer a number of ad-hoc custom search
filter for any API consumer.

