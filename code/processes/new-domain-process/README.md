The new domain process (NDP) is a process that evaluates new domains for
inclusion in the search engine index.   

It visits the root document of each candidate domain, ensures that it's reachable,
verifies that the response is valid HTML, and checks for a few factors such as length
and links before deciding whether to assign the domain to a node.

The NDP process will assign new domains to the node with the fewest assigned domains.

The NDP process is triggered with a goal target number of domains to process, and
will find domains until that target is reached.  If e.g. a goal of 100 is set,
and 50 are in the index, it will find 50 more domains.