#!/bin/bash

./build.sh && (kubectl delete pod -n openmole openmole-connect ; kubectl create -f manifest.yml)
