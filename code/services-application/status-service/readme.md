The status service monitors the search engine's public endpoints, 
and publishes a status page as well as prometheus metrics with the outcome.

The reason for this simple service is to help identify bad deployments
and other issues that might affect the search engine's availability, to
reduce the amount of manual monitoring checking that needs to be done.

The service stores its data in a small sqlite database in the `data` directory,
giving it more resilience to network issues and other problems that might affect
the system.