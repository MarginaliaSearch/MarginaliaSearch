# Uppend

[Uppend](https://github.com/upserve/uppend) - MIT

It's "an append-only, key-multivalue store". Cool project, but we're unceremoniously pillaging just a small piece of 
code they did for calling [memadvise()](https://man7.org/linux/man-pages/man2/madvise.2.html) on off-heap byte buffers.
