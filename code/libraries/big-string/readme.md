# Big String

Microlibrary that offers string compression. This is useful when having to load tens of thousands
of HTML documents in memory during conversion. XML has been described as the opposite of a compression scheme,
and as a result, HTML compresses ridiculously well.

## Configuration 

If the Java property 'bigstring.disabled' is set to true, the BigString class will not compress strings.

## Demo

```java
List<BigString> manyBigStrings = new ArrayList<>();

for (var file : files) {
    // BigString.encode may or may not compress the string 
    // depeneding on its size
    manyBigStrings.add(BigString.encode(readFile(file)));
}

for (var bs : manyBigStrings) {
    String decompressedString = bs.decompress();
    byte[] bytes = bs.getBytes();
    int len = bs.getLength();
}
```