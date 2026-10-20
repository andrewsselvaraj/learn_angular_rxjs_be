#!/usr/bin/env bash
# Deploys the mock exam backend to the OpenShift project you are logged in to (`oc login`, `oc project`).
#   ./scripts/deploy-backend.sh
# Set NAMESPACE to deploy into another existing project, OVERLAY to use another overlay (default: sandbox).
set -euo pipefail

OVERLAY="${OVERLAY:-sandbox}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP=learn-angular-rxjs-be

[ -d "$ROOT/deploy/openshift/overlays/$OVERLAY" ] || { echo "Unknown overlay: $OVERLAY" >&2; exit 1; }
NAMESPACE="${NAMESPACE:-$(oc project -q)}"
oc project "$NAMESPACE" >/dev/null

oc apply -k "$ROOT/deploy/openshift/overlays/$OVERLAY"

# Build from the latest commit on main; the Deployment rolls out when the new image lands.
build="$(oc start-build "$APP" -o name)"
echo "Waiting for $build..."
if ! oc wait "$build" --for=jsonpath='{.status.phase}'=Complete --timeout=20m; then
  oc logs "$build" --tail=50 || true
  echo "$build did not complete" >&2
  exit 1
fi

oc rollout status "deployment/$APP" --timeout=10m

HOST="$(oc get route "$APP" -o jsonpath='{.spec.host}')"
echo "API: https://$HOST/api/exams"
echo "Leaderboard WebSocket: wss://$HOST/ws/leaderboard"
