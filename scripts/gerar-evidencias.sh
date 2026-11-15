#!/usr/bin/env bash
# Regenera as evidências do README a partir dos relatórios mais recentes:
#   docs/evidencias/cobertura-jacoco.png  (target/site/jacoco, gerado pelo scripts/quality.sh)
#   docs/evidencias/bdd-cucumber.png      (target/cucumber)
#   docs/evidencias/swagger.png           (aplicação no ar em http://localhost:8080, ex.: docker compose)
#   docs/arquitetura/png/*.png            (diagramas Mermaid de docs/arquitetura)
#
# Requer Google Chrome (ou Chromium) e Docker. Rodar depois do scripts/quality.sh e atualizar a tabela
# de evidências do README com os números do target/quality/iqc.md.
set -euo pipefail
cd "$(dirname "$0")/.."

CHROME="$(command -v google-chrome || command -v chromium || command -v chromium-browser)"
captura() { "$CHROME" --headless=new --no-sandbox --disable-gpu --hide-scrollbars "$@" 2>/dev/null; }

[ -f target/site/jacoco/index.html ] || { echo "Rode scripts/quality.sh antes" >&2; exit 1; }

PACOTES=$(python3 -I -c "import csv;print(len({r['PACKAGE'] for r in csv.DictReader(open('target/site/jacoco/jacoco.csv'))}))")
captura --window-size=1270,$((180 + PACOTES * 20)) --screenshot=docs/evidencias/cobertura-jacoco.png \
	"file://$PWD/target/site/jacoco/index.html"
captura --window-size=1100,300 --virtual-time-budget=8000 --screenshot=docs/evidencias/bdd-cucumber.png \
	"file://$PWD/target/cucumber/relatorio.html"
if curl -sf http://localhost:8080/oficina/v1/actuator/health >/dev/null; then
	captura --window-size=1400,1500 --virtual-time-budget=10000 --screenshot=docs/evidencias/swagger.png \
		"http://localhost:8080/oficina/v1/swagger-ui/index.html?urls.primaryName=ordem-servico"
else
	echo "Aplicação fora do ar: print do Swagger mantido" >&2
fi

for diagrama in docs/arquitetura/*.mmd; do
	nome=$(basename "$diagrama" .mmd)
	docker run --rm -u "$(id -u):$(id -g)" -v "$PWD/docs/arquitetura:/data" minlag/mermaid-cli:11.4.2 \
		-i "/data/$nome.mmd" -o "/data/png/$nome.png" -b white -s 2 >/dev/null
done
echo "Evidências atualizadas em docs/evidencias e docs/arquitetura/png"
