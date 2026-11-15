#!/usr/bin/env bash
# Valida os manifestos num cluster Kubernetes local (kind), com o mesmo script de deploy do pipeline.
#
# Uso: scripts/k8s-local.sh            sobe (ou reaproveita) o cluster, constrói a imagem e publica
#      scripts/k8s-local.sh --remover  apaga o cluster
#
# Requer Docker, kind e kubectl. A API fica em http://localhost:18080/oficina/v1 enquanto o
# port-forward impresso no final estiver rodando.
set -euo pipefail

CLUSTER=oficina-os
NAMESPACE=oficina-os-local
IMAGEM="oficina-os-service:k8s-local-$(date +%s)"
cd "$(dirname "$0")/.."

if [ "${1:-}" = "--remover" ]; then
	kind delete cluster --name "$CLUSTER"
	exit 0
fi

if ! kind get clusters | grep -qx "$CLUSTER"; then
	kind create cluster --name "$CLUSTER" --wait 120s
fi
kubectl config use-context "kind-$CLUSTER" >/dev/null

docker build -t "$IMAGEM" .
kind load docker-image "$IMAGEM" --name "$CLUSTER"

scripts/deploy-k8s.sh local "$NAMESPACE" "$IMAGEM"

echo
echo "Para acessar a API:"
echo "  kubectl -n $NAMESPACE port-forward service/os-service 18080:80"
echo "  curl http://localhost:18080/oficina/v1/actuator/health"
