# API

The API service acts as a gateway for public API requests, it deals with API keys and rate limiting and so on.

## Central Classes

* [ApiService](src/main/java/nu/marginalia/api/ApiService.java) handles REST requests and delegates to the appropriate handling classes. 