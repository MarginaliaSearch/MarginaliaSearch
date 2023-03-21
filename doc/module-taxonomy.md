# Module Taxonomy

Marginalia has a lot of modules, and their names have specific meanings with architectural consequences. These are outlined below.

## Library

A library is independent of the search engine domain. It solves a single problem. Maybe it's a B-Tree implementation, 
a locality sensitive hash algorithm. Whatever. It does not know what an URL is, or a document. It's more primitive 
than that.

These could hypothetically be broken off and shipped separately, or just yoinked from the codebase and used elsewhere. 
These libraries are co-licensed under MIT to facilitate that, the rest of the search engine is and will be AGPL.

## Feature

A feature is essentially a domain-specific library. It solves some specific problem. Maybe extracting keywords from a 
document, or parsing a search query. Features exist to separate conceptually isolated logic. It may only depend on 
libraries and models.

## Models

A module package contains domain-specific data representations. It may contain light logic related to e.g. serialization, but should
primarily focus on the data.

## APIs

A module package contains domain-specific interface between processes.

## Process

A process is a batch job that reads files and performs some task. It may depend on libraries, features and models. It may not explicitly 
depend on a service or another process.

## Service

A service offers a web service interface. It may depend on libraries, features and models and APIs. It may not explicitly depend on a 
process or another service.
