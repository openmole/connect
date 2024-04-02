#!/bin/bash

eval $(minikube docker-env)
sbt "project application" docker:publishLocal
#minikube image load --daemon=true openmole-connect:0.6-SNAPSHOT
