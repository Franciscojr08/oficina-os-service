#!/usr/bin/env bash
# Verificação completa de qualidade e cálculo do IQC (docs/REGRAS_EXECUCAO_E_QUALIDADE.md).
#
# Uso: scripts/quality.sh
# Requer Java 21 e Docker (Testcontainers e Trivy). Relatórios em target/quality/.
# Sai com código diferente de zero se houver bloqueador ou IQC abaixo de 90.
set -uo pipefail

cd "$(dirname "$0")/.."
QUALITY_DIR=target/quality
STATUS_FILE="$QUALITY_DIR/status.env"
JAR=target/oficina-os-service-0.0.1-SNAPSHOT.jar
TRIVY_IMAGE=aquasec/trivy:0.76.2

# Limpa antes de criar a pasta de relatórios: cobertura e relatórios são todos desta execução
./mvnw -B -q clean
mkdir -p "$QUALITY_DIR"
: > "$STATUS_FILE"

etapa() {
	local nome="$1"
	shift
	echo "==> $nome"
	if "$@" > "$QUALITY_DIR/$nome.log" 2>&1; then
		echo "$nome=0" >> "$STATUS_FILE"
		echo "    ok"
	else
		echo "$nome=1" >> "$STATUS_FILE"
		echo "    FALHOU (veja $QUALITY_DIR/$nome.log)"
	fi
}

trivy() {
	docker run --rm -v trivy-cache:/root/.cache/ -v "$PWD":/src "$TRIVY_IMAGE" "$@"
}

etapa testes ./mvnw -B verify
etapa estilo ./mvnw -B spotless:check
etapa lint ./mvnw -B checkstyle:check
etapa analise_estatica ./mvnw -B spotbugs:check

if command -v docker > /dev/null && docker info > /dev/null 2>&1; then
	# Dependências: varre o JAR, que é exatamente o que vai para produção (dispensa resolver o pom pela rede)
	etapa seguranca_dependencias trivy rootfs --quiet --scanners vuln --severity HIGH,CRITICAL \
		--format json --output "/src/$QUALITY_DIR/trivy-dependencias.json" "/src/$JAR"
	# Segredos versionados e configuração insegura (Dockerfile, manifestos)
	etapa seguranca_repositorio trivy fs --quiet --scanners secret,misconfig --severity HIGH,CRITICAL \
		--skip-files /src/pom.xml --skip-dirs /src/target \
		--format json --output "/src/$QUALITY_DIR/trivy-repositorio.json" /src
else
	echo "==> segurança: Docker indisponível, varredura do Trivy não executada"
	echo "seguranca_dependencias=nao_executado" >> "$STATUS_FILE"
	echo "seguranca_repositorio=nao_executado" >> "$STATUS_FILE"
fi

python3 scripts/iqc.py
