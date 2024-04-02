#!/bin/bash -x 

kubectl delete pod openmole
kubectl run  openmole --port 8080 --image=openmole/openmole:17.0-SNAPSHOT -- openmole-docker --password password

sleep 3

kubectl exec  openmole  -- openmole --version
