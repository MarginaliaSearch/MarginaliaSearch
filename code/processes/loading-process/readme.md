# Loading Process

The loading process reads converted data and inserts it into the database,
as well as creates a journal file that will be used to create a static index for
the index-service.

## Central Classes

* [LoaderMain](java/nu/marginalia/loading/LoaderMain.java) main class.