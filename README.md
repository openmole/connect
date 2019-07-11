# openmole-connect

Connection portal for the OpenMOLE application

## Build & Run##
First, build the javascript:
```sh
$ cd openmole-connect
$ sbt
> go // Build the client JS files and move them to the right place
```

Then, start the server:
```sh
> jetty:start // Start the server
```