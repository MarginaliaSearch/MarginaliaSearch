This is a subsystem that allows on-demand screenshot capture of a website.  

It uses the local browserless API to capture data.  To use this module,
you must have a browserless docker container running on machine, and
then set the `live-capture.browserless-uri` system property to the
address of the browserless container (e.g `http://my-container:3000/`).

When disabled, the subsystem will acknowledge the request, but will not
act on it.

The module will only enable on the primary node of a service to simplify
dealing with race conditions and duplicate requests. 