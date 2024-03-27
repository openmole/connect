#!/bin/bash 

docker run -it -p 8080:8080 openmole-connect:0.6-SNAPSHOT --salt some-salt --kube-off --secret some-secret
