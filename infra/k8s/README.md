# GoldStack on Kubernetes (Phase 7)

This runs the whole GoldStack system in a local **kind** cluster. kind runs Kubernetes nodes as
Docker containers, and Colima runs Docker on this Mac.

## 1. Set up the cluster (once)

```bash
# Colima needs about 10 GiB for the whole stack (the compose setup needed 6)
colima stop && colima start --memory 10 --cpu 4

# Create the cluster (node 80 -> your Mac's 8090)
kind create cluster --config infra/k8s/kind-cluster.yaml

# Ingress controller: routes browser traffic by URL path
kubectl apply -f https://kind.sigs.k8s.io/examples/ingress/deploy-ingress-nginx.yaml
kubectl wait --namespace ingress-nginx --for=condition=ready pod --selector=app.kubernetes.io/component=controller --timeout=180s

# metrics-server: the HPA needs it to read CPU usage (kind uses self-signed kubelet certs)
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
kubectl -n kube-system patch deployment metrics-server --type=json \
  -p='[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-insecure-tls"}]'
```

## 2. Build the images and load them into the cluster

The cluster cannot see images on your Mac, so each image is copied into the kind node. The images
come from the compose build (`docker compose build`).

```bash
kind load docker-image \
  goldstack-api-gateway:local goldstack-frontend:local goldstack-user-service:local \
  goldstack-wallet-service:local goldstack-price-service:local goldstack-order-service:local \
  goldstack-sip-service:local goldstack-portfolio-service:local goldstack-notification-service:local \
  --name goldstack
```

## 3. Deploy

The secret is created from your local `.env`, so real secrets never reach git. The `secret*.yaml`
template in this folder is never applied.

```bash
kubectl apply -f infra/k8s/00-namespace.yaml
kubectl -n goldstack create secret generic goldstack-secrets \
  --from-literal=DB_PASSWORD="$(grep ^DB_PASSWORD= .env | cut -d= -f2-)" \
  --from-literal=REDIS_PASSWORD="$(grep ^REDIS_PASSWORD= .env | cut -d= -f2-)" \
  --from-literal=JWT_SECRET="$(grep ^JWT_SECRET= .env | cut -d= -f2-)"

kubectl apply -f infra/k8s/10-configmap.yaml -f infra/k8s/20-postgres.yaml \
  -f infra/k8s/21-redis.yaml -f infra/k8s/22-kafka.yaml -f infra/k8s/23-zipkin.yaml \
  -f infra/k8s/services/ -f infra/k8s/40-ingress.yaml -f infra/k8s/50-hpa-order-service.yaml
```

Open http://localhost:8090 when the pods are ready.

## 4. Check it

```bash
kubectl -n goldstack get pods                       # READY 1/1 and STATUS Running = healthy
kubectl -n goldstack get deploy,svc,ingress,hpa     # everything else that was created
kubectl -n goldstack logs deploy/wallet-service -f  # live logs of one service
kubectl -n goldstack describe pod <pod-name>        # events: why a pod is stuck
```

## 5. Demos

**Kill a pod and watch it come back.** The Deployment keeps the number of pods at the desired count.

```bash
kubectl -n goldstack delete pod -l app=wallet-service
kubectl -n goldstack get pods -l app=wallet-service -w   # a new pod appears within seconds
```

**Scale sip-service to 3, and show ShedLock keeps the SIP job running once.** Every pod has the
scheduler, but ShedLock lets only one pod take each run's lock. For the demo we make the job run every
minute instead of once a day, by setting its cron expression through an environment variable.

```bash
kubectl -n goldstack set env deploy/sip-service GOLDSTACK_SIP_CRON="0 * * * * *"
kubectl -n goldstack scale deploy/sip-service --replicas=3
kubectl -n goldstack get pods -l app=sip-service               # three pods, all Running

# Wait a minute, then look at the job's log line from all three pods:
kubectl -n goldstack logs -l app=sip-service --prefix --since=2m | grep "SIP run finished"
```

Each minute, only ONE pod prints `SIP run finished`. The other two could not get the lock, so they do
nothing for that run. Put the schedule back afterwards with
`kubectl -n goldstack set env deploy/sip-service GOLDSTACK_SIP_CRON-`.

**Autoscaling for order-service.** The HPA adds pods above 70% CPU, between 1 and 3 pods.

```bash
kubectl -n goldstack get hpa order-service -w
```

## 6. Clean up

```bash
kubectl delete namespace goldstack      # removes the app, the data volume and the secret
kind delete cluster --name goldstack    # removes the whole cluster
```

## What each object does

| File | Object | What it does |
|---|---|---|
| `00-namespace.yaml` | Namespace `goldstack` | A folder for everything in this project. One delete removes it all. |
| `10-configmap.yaml` | ConfigMap `goldstack-config` | Non-secret settings (hostnames, ports, service URLs) as environment variables. |
| `11-secret.example.yaml` | Secret (template only) | Shows the keys of the real secret. The real one is created from `.env`. |
| `20-postgres.yaml` | ConfigMap, PersistentVolumeClaim, Deployment, Service | One Postgres with a disk that survives restarts, and one database per service. |
| `21-redis.yaml` | Deployment, Service | Price cache, quotes and rate limits. Safe to lose: Postgres is the source of truth. |
| `22-kafka.yaml` | Deployment, Service | The event bus, in KRaft mode (no ZooKeeper). |
| `23-zipkin.yaml` | Deployment, Service | Stores the trace spans from every service (Phase 6). |
| `services/*.yaml` | Deployment, Service (per app) | One app each: the image, environment, probes and memory limits. |
| `40-ingress.yaml` | Ingress | One address that routes `/` to the frontend and `/api`, `/ws` to the gateway. |
| `50-hpa-order-service.yaml` | HorizontalPodAutoscaler | Adds order-service pods under load (1 to 3). |

Each app Deployment has two probes. **Readiness** (`/actuator/health/readiness`) decides whether the
pod receives traffic. **Liveness** (`/actuator/health/liveness`) restarts the pod if it is stuck.

## 7. Problems we hit, and why the manifests look the way they do

- **Kafka never registered with its own controller.** Two causes. (a) The `kafka` Service only exposed
  port 9092, but the controller quorum uses 9093, so the Service must expose both. (b) A Service named
  `kafka` makes Kubernetes inject `KAFKA_PORT=tcp://...`, which Kafka reads as its own setting. The fix
  is `enableServiceLinks: false` on every pod. The single-node controller also uses `localhost:9093`,
  because this cluster network cannot reach a pod's own Service name.
- **Java services restarted in a loop.** A liveness probe that starts after 60 seconds killed JVMs that
  were still starting on a busy laptop. The `startupProbe` gives them up to 5 minutes first. The JVM
  flags `-XX:TieredStopAtLevel=1 -XX:+UseSerialGC` make startup much cheaper.
- **Pods stuck with no events.** The first `local-path` storage provisioner started before the API server
  was healthy and crashed. Restarting its pod fixed it.
- **kube-proxy crashed with "too many open files".** Many containers used up the inotify limit. Raising
  `fs.inotify.max_user_instances` and `max_user_watches` inside the kind node fixed it.
- **Rollouts made duplicate pods.** `kubectl rollout restart` and changes to the pod spec create a new
  ReplicaSet while the old one is still running. Deleting the stale ReplicaSet removes the duplicates.
