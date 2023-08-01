# Commons Codec

License: [APL 2.0](http://www.apache.org/licenses/LICENSE-2.0)

This package contains a heavily modified version of the Murmur3 hash from [commons-codec](https://commons.apache.org/proper/commons-codec/)
that cuts some corners but outperforms both Commons Codec and Guava fairly significantly for the particular use cases
we care about being fast: Hashing ASCII/Latin1 strings into a well behaving 64-bit hash.

The method `hashLowerBytes(String data)` performs a zero allocation and zero conversion hash of 
the *lower bytes* of the characters in the provided string.  For ASCII, Latin1, or other 8 bit encodings 
this is identical to hashing the entire string. For other use cases, especially away from the
Latin scripts, this function is possibly a foot-gun.

The method `hashNearlyASCII(String data)` is the same as above, except it's
seeded with Java String's hashCode().  This is a very non-standard modification that
makes it a bit better at dealing with other encodings without measurable performance
impact.

The method `long hash(byte[] data)` hashes the entire byte array.

A non-standard behavior is that the hash function folds the 128 bit 
hash into a 64 bit hash by xor:ing the 128 bit parts. 

## Performance Benchmarks

| Algorithm          | Ops/s             | Remark                                                          | 
|--------------------|-------------------|-----------------------------------------------------------------|
| Guava              | 12,114 ±  439     | allocates byte buffers internally                               |
| Common Codec       | 29,224 ± 1,080    | String.getByte() penalty, long\[2\] allocation, possibly elided |
| MH hash            | 30,885 ±  847     | String.getByte() penalty, zero allocations                      |
| MH hashNearlyASCII | 50,018 ± 399      | Zero allocations, worse characteristics outside Latin1/ASCII    |
| MH hashLowerBytes  | 50,533 ±  478     | Zero allocations, only works for Latin1/ASCII                   |
| String.hashCode()  | 567,381 ± 136,185 | Zero allocations, much weaker algo                              |

