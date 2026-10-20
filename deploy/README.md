# Deploying to OpenShift

Deploys the mock exam backend (Spring Boot 3.5, Java 21) to OpenShift, laid out the same way as
`saas-exam-platform-backend` (works on the Red Hat Developer Sandbox).

| Service                 | Port | Database           | Exposed            |
|-------------------------|------|--------------------|--------------------|
| `learn-angular-rxjs-be` | 8080 | none (in memory)   | public HTTPS Route |

```
deploy/
  docker/Dockerfile                 image build outside the cluster
  openshift/base/                   ImageStream, BuildConfig (S2I), ConfigMap, Deployment, Service, Route
  openshift/overlays/sandbox/       Developer Sandbox: current project
scripts/
  deploy-backend.sh                 applies the overlay, builds the image, rolls out, prints the URL
```

## Deploy

Run the script from the repo root (Git Bash on Windows). Log in with the token from the web console
(your name → **Copy login command**), then:

```bash
./scripts/deploy-backend.sh
```

This deploys into the current project (set `NAMESPACE` for another existing one), applies
`deploy/openshift/overlays/sandbox`, builds the image from this repo's `main` branch and waits for the
rollout. It prints the public URLs, e.g. `https://<route-host>/api/exams` and `wss://<route-host>/ws/leaderboard`.

Redeploy after pushing changes with `oc start-build learn-angular-rxjs-be --follow`.
To build a different branch, change `spec.source.git.ref` in `deploy/openshift/base/buildconfig.yaml`.

Let the Angular app call the API by adding its URL to the allowed origins:

```bash
oc set env deployment/learn-angular-rxjs-be CORS_ALLOWED_ORIGINS=https://<frontend-host>,http://localhost:4200
```

(or edit `CORS_ALLOWED_ORIGINS` in `deploy/openshift/base/configmap.yaml` and re-run the script).

The Developer Sandbox scales deployments to zero after 12 hours; bring it back with
`oc scale deployment/learn-angular-rxjs-be --replicas=1`.

## How it is configured

- **Build**: the BuildConfig runs the Red Hat OpenJDK 21 S2I builder (`mvn -B package -DskipTests`);
  `MAVEN_S2I_ARTIFACT_DIRS=target` points the builder at the jar.
- **Data**: exams, answers and the leaderboard live in memory and reset when the pod restarts. The Deployment
  therefore runs exactly one copy with the `Recreate` strategy.
- **Health**: probes use Spring Boot Actuator's `/actuator/health/liveness` and `/readiness`.
- **TLS**: the Route uses edge termination on the cluster's default certificate and redirects HTTP to HTTPS.
  Its `haproxy.router.openshift.io/timeout: 1h` keeps the leaderboard WebSocket from being cut after 30s idle.

## Building the image outside OpenShift

From the repo root:

```bash
docker build -f deploy/docker/Dockerfile -t learn-angular-rxjs-be .
docker run -p 8080:8080 learn-angular-rxjs-be
```
