# Easy LSH

This a simple [Locality-Sensitive Hash](https://en.wikipedia.org/wiki/Locality-sensitive_hashing)
for document deduplication. Hashes are compared using their hamming distance.

## Central Classes

* [EasyLSH](java/nu/marginalia/lsh/EasyLSH.java)

## Demo

Consider statistical distribution only

```java
var lsh1 = new EasyLSH();
lsh1.addUnordered("lorem");
lsh1.addUnordered("ipsum");
lsh1.addUnordered("dolor");
lsh1.addUnordered("sit");
lsh1.addUnordered("amet");

long hash1 = lsh1.get();

var lsh2 = new EasyLSH();
lsh2.addUnordered("amet");
lsh2.addUnordered("ipsum");
lsh2.addUnordered("lorem");
lsh2.addUnordered("dolor");
lsh2.addUnordered("SEAT");

long hash2 = lsh2.get();

System.out.println(EasyLSH.hammingDistance(lsh1, lsh2));
// 1 -- these are similar

```

Consider order as well as distribution

```java
var lsh1 = new EasyLSH();
lsh1.addOrdered("lorem");
lsh1.addOrdered("ipsum");
lsh1.addOrdered("dolor");
lsh1.addOrdered("sit");
lsh1.addOrdered("amet");

long hash1 = lsh1.get();

var lsh2 = new EasyLSH();
lsh2.addOrdered("amet");
lsh2.addOrdered("ipsum");
lsh2.addOrdered("lorem");
lsh2.addOrdered("dolor");
lsh2.addOrdered("SEAT");


long hash2 = lsh2.get();

System.out.println(EasyLSH.hammingDistance(lsh1, lsh2));
// 5 -- these are not very similar

// note the value is relatively low because there are few words
// and there simply can't be very many differences
// it will approach 32 as documents grow larger
```
