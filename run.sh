#!/bin/bash -x 

#docker run -it -p 8080:8080 openmole-connect:0.6-SNAPSHOT --salt some-salt --kube-off --secret some-secret

#minikube image load --daemon=true openmole-connect:0.6-SNAPSHOT
#minikube image pull openmole/openmole:latest
kubectl delete pod openmole-connect
#kubectl delete pod openmole

#kubectl run openmole --port 8080 --image=openmole/openmole:latest -- --password password
#sleep 3

OPENMOLE_IP=`kubectl get pod openmole --template '{{.status.podIP}}'`

kubectl delete -f manifest.yml
kubectl delete configmap connect-config
kubectl create configmap connect-config --from-file=config.yml 
kubectl create -f manifest.yml

#kubectl run openmole-connect --port 8080 --image=openmole-connect:0.6-SNAPSHOT -- --salt some-salt --kube-off --secret some-secret --openmole-test="http://$OPENMOLE_IP:8080/"
sleep 3


#kubectl get pod openmole-connect --template '{{.status.podIP}}'

kubectl delete service openmole-connect
kubectl expose pod openmole-connect --type=NodePort
minikube service openmole-connect
