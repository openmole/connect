#!/bin/bash

sbt "project application" docker:publishLocal && \
docker save --output /tmp/connect.tar openmole/openmole-connect:1.0-SNAPSHOT && \
sudo k3s ctr images import /tmp/connect.tar
#minikube image load --daemon=true openmole-connect:0.6-SNAPSHOT
