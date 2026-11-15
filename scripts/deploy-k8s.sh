#!/usr/bin/env bash
# Publica o OS Service num cluster Kubernetes a partir de um overlay de k8s/overlays.
# Usado pelo pipeline (overlay eks) e pela validação local no kind (overlay local, scripts/k8s-local.sh).
#
# Uso: scripts/deploy-k8s.sh <overlay> <namespace> <imagem>
#   ex.: scripts/deploy-k8s.sh eks oficina 123456789012.dkr.ecr.us-east-1.amazonaws.com/oficina-os-service:abc123
#
# Com CRIAR_SECRETS=true, cria os Secrets a partir destas variáveis de ambiente (no pipeline vêm dos
# GitHub Secrets; nunca são versionadas):
#   OS_DATASOURCE_URL, OS_DATASOURCE_USERNAME, OS_DATASOURCE_PASSWORD, SECURITY_JWT_SECRET,
#   RABBITMQ_USERNAME, RABBITMQ_PASSWORD
set -euo pipefail

OVERLAY="${1:?informe o overlay (eks ou local)}"
NAMESPACE="${2:?informe o namespace}"
IMAGEM="${3:?informe a imagem completa, com tag}"
TIMEOUT="${ROLLOUT_TIMEOUT:-300s}"

RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
DIR_OVERLAY="$RAIZ/k8s/overlays/$OVERLAY"
[ -d "$DIR_OVERLAY" ] || { echo "Overlay inexistente: $DIR_OVERLAY" >&2; exit 1; }

diagnostico() {
	echo "::group::Diagnóstico do namespace $NAMESPACE"
	kubectl get all,hpa -n "$NAMESPACE" || true
	kubectl get events -n "$NAMESPACE" --sort-by=.metadata.creationTimestamp | tail -30 || true
	kubectl describe deployment os-service -n "$NAMESPACE" | tail -40 || true
	kubectl logs deployment/os-service -n "$NAMESPACE" --tail=100 || true
	echo "::endgroup::"
}
trap 'diagnostico' ERR

# Overlay temporário que só troca a imagem: kubectl kustomize resolve, sem depender do binário kustomize
TEMP="$RAIZ/k8s/overlays/.deploy-$$"
trap 'rm -rf "$TEMP"' EXIT
mkdir -p "$TEMP"
cat > "$TEMP/kustomization.yaml" <<YAML
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
resources:
  - ../$OVERLAY
images:
  - name: oficina-os-service
    newName: ${IMAGEM%:*}
    newTag: ${IMAGEM##*:}
# Vai no mesmo apply da imagem, para o histórico do rollout registrar a versão certa
patches:
  - target:
      kind: Deployment
      name: os-service
    patch: |-
      - op: add
        path: /metadata/annotations
        value:
          kubernetes.io/change-cause: "imagem $IMAGEM"
YAML

echo "==> namespace $NAMESPACE"
kubectl apply -f "$DIR_OVERLAY/namespace.yaml"

REINICIAR=false
if [ "${CRIAR_SECRETS:-false}" = "true" ]; then
	FALTANDO=""
	for variavel in OS_DATASOURCE_URL OS_DATASOURCE_USERNAME OS_DATASOURCE_PASSWORD SECURITY_JWT_SECRET \
		RABBITMQ_USERNAME RABBITMQ_PASSWORD; do
		[ -n "${!variavel:-}" ] || FALTANDO="$FALTANDO $variavel"
	done
	if [ -n "$FALTANDO" ]; then
		echo "::error::Secrets não configurados no GitHub:$FALTANDO (ver README, seção CI/CD)" >&2
		exit 1
	fi

	echo "==> secrets"
	SAIDA=$(
		kubectl create secret generic os-service-secret --namespace "$NAMESPACE" \
			--from-literal=SPRING_DATASOURCE_URL="$OS_DATASOURCE_URL" \
			--from-literal=SPRING_DATASOURCE_USERNAME="$OS_DATASOURCE_USERNAME" \
			--from-literal=SPRING_DATASOURCE_PASSWORD="$OS_DATASOURCE_PASSWORD" \
			--from-literal=SECURITY_JWT_SECRET="$SECURITY_JWT_SECRET" \
			--from-literal=SPRING_RABBITMQ_USERNAME="$RABBITMQ_USERNAME" \
			--from-literal=SPRING_RABBITMQ_PASSWORD="$RABBITMQ_PASSWORD" \
			--dry-run=client --output yaml | kubectl apply -f -
		kubectl create secret generic rabbitmq-secret --namespace "$NAMESPACE" \
			--from-literal=RABBITMQ_DEFAULT_USER="$RABBITMQ_USERNAME" \
			--from-literal=RABBITMQ_DEFAULT_PASS="$RABBITMQ_PASSWORD" \
			--dry-run=client --output yaml | kubectl apply -f -
	)
	echo "$SAIDA"
	# Pod não relê Secret alterado: se algum mudou, a aplicação precisa ser reiniciada
	if echo "$SAIDA" | grep -qE "configured"; then
		REINICIAR=true
	fi
fi

echo "==> manifestos ($OVERLAY) com a imagem $IMAGEM"
kubectl apply -k "$TEMP"

if [ "$REINICIAR" = "true" ] && kubectl get deployment os-service -n "$NAMESPACE" >/dev/null 2>&1; then
	echo "==> secrets alterados: reiniciando o os-service"
	kubectl rollout restart deployment/os-service -n "$NAMESPACE"
fi

echo "==> aguardando o rollout"
kubectl rollout status statefulset/rabbitmq -n "$NAMESPACE" --timeout="$TIMEOUT"
kubectl rollout status deployment/os-service -n "$NAMESPACE" --timeout="$TIMEOUT"

echo "==> conferindo a imagem em execução"
APLICADA=$(kubectl get deployment os-service -n "$NAMESPACE" \
	-o jsonpath='{.spec.template.spec.containers[?(@.name=="os-service")].image}')
if [ "$APLICADA" != "$IMAGEM" ]; then
	echo "::error::Deployment com a imagem $APLICADA, esperada $IMAGEM" >&2
	exit 1
fi
# Pods do ReplicaSet antigo ainda em encerramento (deletionTimestamp preenchido) aparecem como
# Running por alguns segundos: só contam os que não estão saindo
DIFERENTES=$(kubectl get pods -n "$NAMESPACE" -l app.kubernetes.io/name=os-service \
	--field-selector=status.phase=Running \
	-o jsonpath='{range .items[*]}{.metadata.deletionTimestamp}{"|"}{.spec.containers[0].image}{"\n"}{end}' \
	| grep '^|' | cut -d'|' -f2 | grep -vx "$IMAGEM" || true)
if [ -n "$DIFERENTES" ]; then
	echo "::error::Pods rodando outra imagem: $DIFERENTES" >&2
	exit 1
fi

echo "Deploy concluído: $IMAGEM"
