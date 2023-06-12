# Monkey Patched GSON

Stanford OpenNLP - Apache-2.0

## Rationale

GSON makes some assumptions that make it not work very well
for deserializing extremely large JSON objects. This patch 
makes the code technically leak memory, but the way it's used
makes this not much of a problem.

It should only be applied to the converter or possibly
loader processes, not the services.