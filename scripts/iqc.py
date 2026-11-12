#!/usr/bin/env python3
"""Calcula o Índice de Qualidade do Código (IQC) a partir dos relatórios gerados por quality.sh.

Critérios, pesos e bloqueadores: docs/REGRAS_EXECUCAO_E_QUALIDADE.md, seção 4.
Saída: tabela no terminal e em target/quality/iqc.md. Código de saída 1 se IQC < 90 ou se
houver bloqueador.
"""
import glob
import json
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

RAIZ = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
QUALITY = os.path.join(RAIZ, "target", "quality")
FONTES = "src/main/java/"
IQC_MINIMO = 90.0
COBERTURA_ALTERADA_ALVO = 90.0
COBERTURA_GLOBAL_MINIMA = 80.0  # exigência do Tech Challenge, por serviço

os.chdir(RAIZ)


def ler_status():
    status = {}
    caminho = os.path.join(QUALITY, "status.env")
    if os.path.exists(caminho):
        for linha in open(caminho, encoding="utf-8"):
            if "=" in linha:
                chave, valor = linha.strip().split("=", 1)
                status[chave] = valor
    return status


def git(*args):
    resultado = subprocess.run(["git", *args], capture_output=True, text=True)
    return resultado.stdout if resultado.returncode == 0 else ""


def resultado_testes():
    total = falhas = 0
    for arquivo in glob.glob("target/surefire-reports/TEST-*.xml"):
        suite = ET.parse(arquivo).getroot()
        total += int(suite.get("tests", 0))
        falhas += int(suite.get("failures", 0)) + int(suite.get("errors", 0))
    return total, falhas


def linhas_jacoco():
    """{caminho_relativo: {linha: coberta}} só com linhas executáveis (as que o JaCoCo instrumenta)."""
    caminho = "target/site/jacoco/jacoco.xml"
    if not os.path.exists(caminho):
        return None, None
    raiz = ET.parse(caminho).getroot()
    mapa = {}
    for pacote in raiz.iter("package"):
        for fonte in pacote.iter("sourcefile"):
            rel = f"{FONTES}{pacote.get('name')}/{fonte.get('name')}"
            mapa[rel] = {int(l.get("nr")): int(l.get("ci")) > 0 for l in fonte.iter("line")}
    total = raiz.find("counter[@type='LINE']")
    cobertas = int(total.get("covered"))
    global_pct = 100.0 * cobertas / (cobertas + int(total.get("missed")))
    return mapa, global_pct


def linhas_alteradas():
    """Linhas novas ou alteradas em src/main/java em relação à main (inclui arquivos não versionados)."""
    base = ""
    for ref in ("origin/main", "main"):
        base = git("merge-base", "HEAD", ref).strip()
        if base:
            break
    alteradas = {}
    if base:
        arquivo = None
        for linha in git("diff", "-U0", base, "--", FONTES).splitlines():
            if linha.startswith("+++ "):
                arquivo = linha[6:] if linha.startswith("+++ b/") else None
            elif linha.startswith("@@") and arquivo:
                inicio, _, qtd = re.search(r"\+(\d+)(,(\d+))?", linha).groups()
                qtd = 1 if qtd is None else int(qtd)
                alteradas.setdefault(arquivo, set()).update(range(int(inicio), int(inicio) + qtd))
    for arquivo in git("ls-files", "--others", "--exclude-standard", "--", FONTES).splitlines():
        with open(arquivo, encoding="utf-8") as f:
            alteradas[arquivo] = set(range(1, sum(1 for _ in f) + 1))
    return alteradas, base


def cobertura_alterada(mapa, alteradas):
    executaveis = cobertas = 0
    for arquivo, linhas in alteradas.items():
        cobertura = mapa.get(arquivo, {})
        for numero in linhas & cobertura.keys():
            executaveis += 1
            cobertas += cobertura[numero]
    return (100.0 * cobertas / executaveis if executaveis else None), executaveis


def contar_checkstyle():
    caminho = "target/checkstyle-result.xml"
    if not os.path.exists(caminho):
        return None
    return sum(1 for _ in ET.parse(caminho).getroot().iter("error"))


def contar_spotbugs():
    caminho = "target/spotbugsXml.xml"
    if not os.path.exists(caminho):
        return None
    return sum(1 for _ in ET.parse(caminho).getroot().iter("BugInstance"))


def contar_trivy(nome, campo):
    caminho = os.path.join(QUALITY, nome)
    if not os.path.exists(caminho):
        return None
    dados = json.load(open(caminho, encoding="utf-8"))
    return sum(len(r.get(campo) or []) for r in dados.get("Results") or [])


def verificar_documentacao():
    """Documentação compatível com a configuração: cada variável de ambiente lida pela aplicação
    precisa estar no README; comandos essenciais precisam estar descritos."""
    checagens = []
    readme = open("README.md", encoding="utf-8").read() if os.path.exists("README.md") else ""
    variaveis = set()
    for props in glob.glob("src/main/resources/application*.properties"):
        variaveis |= set(re.findall(r"\$\{([A-Z][A-Z0-9_]+)", open(props, encoding="utf-8").read()))
    faltando = sorted(v for v in variaveis if v not in readme)
    checagens.append(("variáveis de ambiente no README", not faltando, ", ".join(faltando)))
    for comando in ("./mvnw verify", "docker compose up", "scripts/quality.sh"):
        checagens.append((f"README descreve `{comando}`", comando in readme, ""))
    checagens.append((".env.example presente", os.path.exists(".env.example"), ""))
    return checagens


def main():
    status = ler_status()
    bloqueadores = []
    criterios = []  # (nome, peso, nota 0..1 ou None se não aplicável, detalhe)

    total, falhas = resultado_testes()
    if total == 0:
        criterios.append(("Testes automatizados", 25, 0.0, "nenhum teste executado"))
        bloqueadores.append("nenhum teste executado")
    else:
        criterios.append(("Testes automatizados", 25, (total - falhas) / total, f"{total - falhas}/{total} aprovados"))
        if falhas:
            bloqueadores.append(f"{falhas} teste(s) falhando")

    mapa, cobertura_global = linhas_jacoco()
    if mapa is None:
        criterios.append(("Cobertura do código alterado", 20, 0.0, "relatório JaCoCo ausente"))
        bloqueadores.append("cobertura não medida")
    else:
        alteradas, base = linhas_alteradas()
        pct, executaveis = cobertura_alterada(mapa, alteradas)
        if pct is None:
            criterios.append(("Cobertura do código alterado", 20, None, "nenhuma linha executável alterada"))
        else:
            nota = min(1.0, pct / COBERTURA_ALTERADA_ALVO)
            detalhe = f"{pct:.1f}% de {executaveis} linhas (alvo {COBERTURA_ALTERADA_ALVO:.0f}%); global {cobertura_global:.1f}%"
            criterios.append(("Cobertura do código alterado", 20, nota, detalhe))
        if cobertura_global < COBERTURA_GLOBAL_MINIMA:
            bloqueadores.append(f"cobertura global {cobertura_global:.1f}% abaixo de {COBERTURA_GLOBAL_MINIMA:.0f}%")

    bugs = contar_spotbugs()
    ok_estatica = bugs == 0 and status.get("analise_estatica") == "0"
    criterios.append(("Análise estática", 20, 1.0 if ok_estatica else 0.0,
                      "SpotBugs + FindSecBugs: " + ("não executado" if bugs is None else f"{bugs} achado(s)")))
    if not ok_estatica:
        bloqueadores.append("erro de análise estática")

    violacoes = contar_checkstyle()
    estilo_ok = status.get("estilo") == "0"
    lint_ok = violacoes == 0 and status.get("lint") == "0"
    criterios.append(("Estilo e lint", 15, (estilo_ok + lint_ok) / 2,
                      f"Spotless {'ok' if estilo_ok else 'com violações'}; Checkstyle "
                      + ("não executado" if violacoes is None else f"{violacoes} violação(ões)")))

    vulns = contar_trivy("trivy-dependencias.json", "Vulnerabilities")
    segredos = contar_trivy("trivy-repositorio.json", "Secrets")
    configs = contar_trivy("trivy-repositorio.json", "Misconfigurations")
    if None in (vulns, segredos, configs):
        criterios.append(("Segurança e confiabilidade", 15, 0.0, "Trivy não executado"))
        bloqueadores.append("segurança não verificada (Trivy não executado)")
    else:
        criterios.append(("Segurança e confiabilidade", 15, 1.0 if vulns + segredos + configs == 0 else 0.0,
                          f"{vulns} vulnerabilidade(s) HIGH/CRITICAL, {segredos} segredo(s), {configs} configuração(ões) insegura(s)"))
        if vulns:
            bloqueadores.append(f"{vulns} vulnerabilidade(s) HIGH/CRITICAL")
        if segredos:
            bloqueadores.append(f"{segredos} segredo(s) versionado(s)")

    docs = verificar_documentacao()
    aprovadas = sum(1 for _, ok, _ in docs if ok)
    pendentes = "; ".join(f"{n} ({d})" if d else n for n, ok, d in docs if not ok)
    criterios.append(("Documentação e manutenção", 5, aprovadas / len(docs),
                      f"{aprovadas}/{len(docs)} checagens" + (f" — pendente: {pendentes}" if pendentes else "")))

    aplicaveis = [c for c in criterios if c[2] is not None]
    peso_total = sum(c[1] for c in aplicaveis)
    iqc = sum(c[1] * c[2] for c in aplicaveis) * 100.0 / peso_total

    linhas = ["# Índice de Qualidade do Código (IQC)", "",
              "| Critério | Peso | Pontos | Detalhe |", "|---|---:|---:|---|"]
    for nome, peso, nota, detalhe in criterios:
        pontos = "n/a" if nota is None else f"{peso * nota * 100.0 / peso_total:.1f}"
        linhas.append(f"| {nome} | {peso} | {pontos} | {detalhe} |")
    linhas += ["", f"**IQC: {iqc:.1f} / 100** (mínimo {IQC_MINIMO:.0f})", ""]
    linhas.append("**Bloqueadores:** " + ("; ".join(bloqueadores) if bloqueadores else "nenhum"))
    aprovado = iqc >= IQC_MINIMO and not bloqueadores
    linhas += ["", f"**Resultado: {'APROVADO' if aprovado else 'REPROVADO'}**"]

    relatorio = "\n".join(linhas) + "\n"
    with open(os.path.join(QUALITY, "iqc.md"), "w", encoding="utf-8") as f:
        f.write(relatorio)
    print()
    print(relatorio)
    return 0 if aprovado else 1


if __name__ == "__main__":
    sys.exit(main())
