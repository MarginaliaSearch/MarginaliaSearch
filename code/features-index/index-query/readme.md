# Index Query

Contains interfaces and primitives for creating and evaluating queries against the indices.

Central to interacting with the query interface is the `IndexQuery` class. This class is used 
to create and evaluate queries against the index. The class will fill a `LongQueryBuffer` with
the results of the query. 

This is a relatively light library consisting of a few classes and interfaces. Many of the
interfaces are implemented within the index-service module.


## Central Classes

* [IndexQuery](src/main/java/nu/marginalia/index/query/IndexQuery.java)
* [query/filter](src/main/java/nu/marginalia/index/query/filter/)

## See Also

* [index/index-reverse](../index-reverse) implements many of these interfaces.
* [libraries/array](../../libraries/array)
* [libraries/array/.../LongQueryBuffer](../../libraries/array/src/main/java/nu/marginalia/array/buffer/LongQueryBuffer.java)