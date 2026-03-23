This function implements an NSFW filter, as well as tools for training the filter,
and gathering training data.

## Training the model

The model trainer reads sample data from `${ROOT}/run/training-data/nsfw`.
It can be invoked by running

```shell
$ ./gradlew trainNsfwModel
```

This will write a model to `run/model/nsfw-model`.  This needs to be copied to the model directory of
an installation to have effect.

## Gathering samples

Gathered samples are labeled automatically with a local LLM.  For this operation you need
ollama installed, as well as some appropriate model downloaded.  By default the labeler
uses qwen3:8b.  

To gather samples, run 

```shell
$ ./gradlew nsfwModelGrowSampleData  --console=plain -q

# you can also do e.g.
$ LABELING_MODEL=qwen3:4b ./gradlew nsfwModelGrowSampleData  --console=plain -q
```

You will be prompted for an operation mode.  Two modes exist:

* A query-based approach where the API of Marginalia Search is queried, and samples are labeled based on that.
* A document db based approach where the db is scanned for false positives, which are relabeled and used to correct the model.