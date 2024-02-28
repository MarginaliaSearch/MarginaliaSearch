The domain-info subsystem is responsible for answering questions about a domain,
such as its IP address, rank, similar domains, and so on.  

This subsystem keeps a copy of the link graph in memory, which is quite RAM intensive,
but also very fast.  It should only be used for the search application, the system 
itself should use the [link-graph](../link-graph) subsystem.