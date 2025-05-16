# OpenMOLE connect

OpenMOLE connect is a server meant to be deployed in a kubernetees cluster in order to provide a multi-user [OpenMOLE](https://openmole.org) service.

Here is the recepie to deploy a K3S cluster and deploy OpenMOLE connect on this cluster. 

## Prerequisite

To complete the installation of the multi-user OpenMOLE server you'll need:
- a debian (or ubuntu) machine accesible via ssh and that is able to accept inbound connections on ports 80 and 443
- a root account on the machine
- a dns entry pointing to the IP of this machine
- optionnaly an account on an SMTP mail server accesible via start tls (more could be easily supported, please can ask for it) to send the notification to the users   
- optionally a minio server to backup the user data stored in longhorn

The info needed are:
- MASTER_HOST: the dns name of the host mochine
- MASTER_USER: the name of the sudoer account on the machine
- EMAIL_SERVER: the dns name of the smtp server
- EMAIL_PORT: the port of the smtp start tls port (generally port 587)
- EMAIL_USER: the user to login to the email server
- EMAIL_PASSWORD: the password to login to the email server
- EMAIL_SENDER_ADDRESS: an email sender address
- MINIO_URL: the URL of the minio service
- MINIO_ACCESS_KEY: a minio API key
- MINIO_SECRET_KEY:: a minio API key secret 

Install [Helm](https://helm.sh/) on your machine

## Deploying k3s head node

Instantiate a machine and make sure to open all the ports in your security group if you are on openstack.

Deploy the server node:
```
ssh ${MASTER_USER}@${MASTER_HOST}
sudo apt install open-iscsi # requiered for longhorn
curl -sfL https://get.k3s.io | INSTALL_K3S_VERSION="v1.31.5+k3s1" INSTALL_K3S_EXEC="server" sh -s -
```

To prevent timeouts when uploading/downloading files you should create the following file `/var/lib/rancher/k3s/server/manifests/traefik-config.yaml` on the k3s server. 
The content of the file should be:
```
apiVersion: helm.cattle.io/v1
kind: HelmChartConfig
metadata:
  name: traefik
  namespace: kube-system
spec:
  valuesContent: |-
    additionalArguments:
      - "--entryPoints.web.transport.respondingTimeouts.readTimeout=0"
      - "--entryPoints.web.transport.respondingTimeouts.writeTimeout=0"
      - "--entryPoints.web.transport.respondingTimeouts.idleTimeout=0"
      - "--entryPoints.websecure.transport.respondingTimeouts.readTimeout=0"
      - "--entryPoints.websecure.transport.respondingTimeouts.writeTimeout=0"
      - "--entryPoints.websecure.transport.respondingTimeouts.idleTimeout=0"
```

And restart k3s: 
`sudo systemctl restart k3s`

Test that the server works. Copy the file `/etc/rancher/k3s/k3s.yaml` on your machine. In the file, replace the master IP address with `MASTER_HOST` address.

```
export KUBECONFIG=$PWD/k3s.yml
kubectl get node
```


## Optionnaly, install the dashboard

```
helm repo add kubernetes-dashboard https://kubernetes.github.io/dashboard/
helm upgrade --install kubernetes-dashboard kubernetes-dashboard/kubernetes-dashboard --create-namespace --namespace kubernetes-dashboard
```

Create a file named `token.yml` on your local computer, and fill it with the following content:
```
apiVersion: v1
kind: ServiceAccount
metadata:
  name: admin-user
  namespace: kubernetes-dashboard
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: admin-user
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: cluster-admin
subjects:
- kind: ServiceAccount
  name: admin-user
  namespace: kubernetes-dashboard
```

Then you can create an token an a proxy for the dashboard:
```
kubectl create -f token.yml
kubectl -n kubernetes-dashboard create token admin-user
kubectl -n kubernetes-dashboard port-forward svc/kubernetes-dashboard-kong-proxy 8443:443
```

The dashboard is accesible from within your local browser at `https://localhost:8443`.

## Install longhorn

Longhorn is the storage system used by connect.

Install longhorn with the following command:
```
kubectl apply -f https://raw.githubusercontent.com/longhorn/longhorn/v1.8.0/deploy/longhorn.yaml
```

The installation documentation for longhorn can be [found here](https://longhorn.io/docs/1.8.1/deploy/install/install-with-kubectl/).

Accesing the longhorn UI:
```
kubectl port-forward -n longhorn-system svc/longhorn-frontend 8080:80
```

## Install cert manager 

```
helm repo add jetstack https://charts.jetstack.io --force-update
helm install cert-manager jetstack/cert-manager --namespace cert-manager --create-namespace --version v1.16.0 --set crds.enabled=true
```

Then apply the following yaml:
```
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt-prod
spec:
  acme:
    server: https://acme-v02.api.letsencrypt.org/directory
    email: yourmail@domain.com
    privateKeySecretRef:
      name: letsencrypt-prod
    solvers:
    - http01:
        ingress:
          class: traefik
```

## Deploy OpenMOLE Connect

Generate a sercret and a salt at random and store back them up somewhere. You may want to use `pwgen 20` to get two random strings.

```
apiVersion: v1
kind: Namespace
metadata:
  name: openmole
  labels:
    name: openmole
---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: openmole-admin-sa
  namespace: openmole
---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: openmole-admin-role
  namespace: openmole
rules:
- apiGroups:
  - "*"
  resources: 
  - "*" #["pods", "deployments" ]
  verbs: 
  - "*" #["list"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: openmole-role-binding
  namespace: openmole
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: Role
  name: openmole-admin-role
subjects:
- kind: ServiceAccount
  name: openmole-admin-sa
  namespace: openmole
---
kind: StorageClass
apiVersion: storage.k8s.io/v1
metadata:
  name: openmole-storage
  namespace: openmole
provisioner: driver.longhorn.io
allowVolumeExpansion: true
parameters:
  numberOfReplicas: "1"
  dataLocality: "best-effort"
  staleReplicaTimeout: "2880" # 48 hours in minutes
  fromBackup: ""
---
apiVersion: v1
data:
  config.yml: |
    salt: "some-salt" # replace with the random salt
    secret: "some-secret" # replace with the ranodm secret
    kube:
     storageClassName: openmole-storage
     storageSize: 20480
    openmole:
      versionHistory: 5
      minimumVersion: 17
    smtp:
      server: "smtp.mydomain.net" # replace with EMAIL_SERVER
      port: 587 # replace with EMAIL_PORT
      user: user # replace with EMAIL_USER
      password: password # replace with EMAIL_PASSWORD
      from: no-reply@mydomain.net # replace with EMAIL_SENDER_ADDRESS
    shutdown:
      days: 30
      checkAt: 8
      remind: [1, 3, 7]
kind: ConfigMap
metadata:
  creationTimestamp: null
  name: connect-config
  namespace: openmole
---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: connect-pv-claim
  namespace: openmole
spec:
  storageClassName: openmole-storage
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 20Gi
---
apiVersion: v1
kind: Pod
metadata:
  name: openmole-connect
  namespace: openmole
  labels:
    app.kubernetes.io/name: openmole-connect
spec:
  containers:
  - name: openmole-connect
    image: openmole/openmole-connect:1.0-SNAPSHOT
    imagePullPolicy: "Always"
    args:
      - --config-file
      - "/etc/connect-config/config.yml"
    ports:
      - containerPort: 8080
    volumeMounts:
      - name: connect-config
        mountPath: "/etc/connect-config"
        readOnly: true
      - mountPath: "/home/demiourgos728"
        name: connect-pv-storage
  securityContext:
    fsGroup: 1001
  volumes:
    - name: connect-config
      configMap:
        name: connect-config
    - name: connect-pv-storage
      persistentVolumeClaim:
        claimName: connect-pv-claim
  serviceAccountName: openmole-admin-sa
---
apiVersion: v1
kind: Service
metadata:
  name: openmole-connect-service
  namespace: openmole
spec:
  selector:
    app.kubernetes.io/name: openmole-connect
  ports:
    - protocol: TCP
      port: 8080
      nodePort: 30080
  type: NodePort
---
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: openmole-connect-ingress
  namespace: openmole
  annotations:
    cert-manager.io/cluster-issuer: letsencrypt-prod
spec:
  rules:
  - host: host.mydomain.ext # replace with MASTER_HOST
    http:
      paths:
      - backend:
          service:
            name: openmole-connect-service
            port:
              number: 8080
        path: /
        pathType: Prefix
  tls:
  - hosts:
    - host.mydomain.ext # replace with MASTER_HOST
    secretName: letsencrypt-prod
```

## Optionaly, configure the backup

You can configure a minio server to be able to backup your service.

To the get the base64 info:
```
echo -n <URL> | base64
echo -n <Access Key> | base64
echo -n <Secret Key> | base64
--------------------------------------------------------
echo -n http://192.168.1.66:9000/ | base64 # replace with MINIO_URL
echo -n t6dstDsYQuf7pbSj | base64 # replacet with MINIO_ACCESS_KEY
echo -n Q9RPWKxZ4bUIqBSzuOY6TLkFkXcHszRU | base64 # replace with MINIO_SERCRET_KEY
```

Replace the base64 info in the followin yaml and apply it:
```
apiVersion: v1
kind: Secret
metadata:
  name: longhorn-minio-credentials
  namespace: longhorn-system
type: Opaque
data:
  AWS_ENDPOINTS: aHR0cDovLzE5Mi4xNjguMS42Njo5MDAwLw==
  AWS_ACCESS_KEY_ID: dDZkc3REc1lRdWY3cGJTag==
  AWS_SECRET_ACCESS_KEY: UTlSUFdLeFo0YlVJcUJTenVPWTZUTGtGa1hjSHN6UlU=
```

Then you can use longhorn-minio-credentials as a credential in your backup field of longhorn UI

## Add a node to the cluster

When your node is full you can add a node to the cluster.

Deploy an k3s node. Find the k3s token on the head node /var/lib/rancher/k3s/server/node-token. Store it in K3S_TOKEN environment variable on the node.
```
sudo apt install open-iscsi # requiered for longhorn
curl -sfL https://get.k3s.io | K3S_URL=https://${MASTER_HOST}:6443 K3S_TOKEN="${K3S_TOKEN}" sh -s - 
```

Check that you have 2 nodes now.




