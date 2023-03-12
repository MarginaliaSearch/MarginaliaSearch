# Monkey Patched OpenNLP

Stanford OpenNLP - Apache-2.0

## Rationale

OpenNLP's sentence detector uses a slow StringBuffer instead of a StringBuilder where it makes no
no sense to do so. This makes it much slower than it needs to be. I've found no way to file issues with the 
project to get it fixed. Instead we're doing this monkey patch where the class is overridden with something 
better.

