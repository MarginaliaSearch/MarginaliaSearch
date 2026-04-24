# Roadmap for 2026

## Snippet Generation

Most "real" search engines extract a live snippet for each search result,
whereas Marginalia uses a static summary that is petty buggy.  It would
significantly improve the apparent quality of the search results to have
this feature.

### Plan:

* Store document data somehow (block-wise compression)
* Store document data offsets in forward index
* Implement snippet extraction based on term position density

## Site viewer improvements

It would be very nice to have a more conversational view of the site viewer,
to see recent updates, backlinks and so on for websites this is appropriate.  

### Plan: 

* Issue #280 (Add specialized index lookups for document listing and backlinks listing)
* Make live crawling and RSS feed fetching less flakey
* Draw the rest of the owl

## Migrate off zookeeper

This is a heavy dependency and I think for our needs we could roll something much more lightweight

## Migrate off docker

Docker has a kinda insane overhead cost and it's in general hard to keep secure.  

### Plan:

* Something something systemd+cgroups+namespaces
