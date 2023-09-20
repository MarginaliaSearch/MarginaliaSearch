This tool converts from stackexchange's 7z-compressed XML
format to a sqlite database that is digestible by the search engine.

See [features-convert/stackexchange-xml](../../features-convert/stackexchange-xml) for
an explanation why this is necessary. 

Stackexchange's data dumps can be downloaded from archive.org
here: [https://archive.org/details/stackexchange](https://archive.org/details/stackexchange)

<b>Usage</b>

```shell
$ stackexchange-converter domain-name input.7z output.db
```

Stackexchange is relatively conservative about allowing
new questions, so this is a job that doesn't run more than once.

<b>Note</b>:  Reading and writing these db files is *absurdly* slow
on a mechanical hard-drive.

## See Also

* [features-convert/stackexchange-xml](../../features-convert/stackexchange-xml)