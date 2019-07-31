# openmole-connect

Connection portal for the OpenMOLE multi-user application
It can be built and run locally or as a docker image.
  
## Locally
### Build the javascript:
```sh
$ cd openmole-connect
$ sbt
> go // Build the client JS files and move them to the right place
```
### Run the server
Simply start the server from the sbt prompt:
```sh
> jetty:start // Start the server
```

## Build & Run from Docker
### Build the image
```sh
$ cd openmole-connect
$ sbt application/docker:publishLocal
```

### Run the container
```sh 
docker run --name connectApplication -p 7700:8080 openmole-connect:$VERSION
```
