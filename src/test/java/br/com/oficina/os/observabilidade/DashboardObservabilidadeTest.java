package br.com.oficina.os.observabilidade;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.oficina.os.mensageria.consumo.ConsumidorEventos;
import br.com.oficina.os.mensageria.consumo.ProcessadorEventos;
import br.com.oficina.os.mensageria.outbox.OutboxPublicador;
import br.com.oficina.os.saga.application.SagaObservabilidade;
import br.com.oficina.os.shared.observabilidade.OrdemServicoObservabilidade;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * O dashboard (docs/observabilidade/dashboard-saga.json) consulta eventos e métricas pelo nome. Se o
 * código renomear um deles, o painel fica vazio sem erro nenhum: este teste falha antes.
 */
class DashboardObservabilidadeTest {

	private static final Path DASHBOARD = Path.of("docs/observabilidade/dashboard-saga.json");

	/** Tipos de dado do próprio New Relic, enviados pelo agent. */
	private static final Set<String> TIPOS_DO_AGENT = Set.of("Metric", "Span", "Transaction");

	private static final Set<String> EVENTOS_DO_CODIGO = Set.of(
			SagaObservabilidade.EVENTO_TRANSICAO,
			SagaObservabilidade.EVENTO_REENVIO,
			ConsumidorEventos.EVENTO_REJEITADA,
			OutboxPublicador.EVENTO_FALHA,
			OrdemServicoObservabilidade.EVENTO_STATUS_ALTERADO,
			OrdemServicoObservabilidade.EVENTO_ETAPA_CONCLUIDA,
			OrdemServicoObservabilidade.EVENTO_TRANSICAO_INVALIDA);

	private static final Set<String> METRICAS_DO_CODIGO = Set.of(
			OutboxPublicador.METRICA_PENDENTES,
			ProcessadorEventos.CONTADOR_PROCESSADAS,
			ProcessadorEventos.CONTADOR_DUPLICADAS);

	private static final Pattern FROM = Pattern.compile("FROM\\s+(\\w+)");
	private static final Pattern METRICA = Pattern.compile("'(Custom/[^']+)'");

	@Test
	void todaConsultaUsaEventosEMetricasQueOCodigoEnvia() throws IOException {
		List<String> consultas = consultas();
		TreeSet<String> tipos = new TreeSet<>();
		TreeSet<String> metricas = new TreeSet<>();
		for (String consulta : consultas) {
			coletar(FROM, consulta, tipos);
			coletar(METRICA, consulta, metricas);
		}

		tipos.removeAll(TIPOS_DO_AGENT);
		assertThat(tipos).as("eventos consultados no dashboard").isSubsetOf(EVENTOS_DO_CODIGO);
		assertThat(metricas).as("métricas consultadas no dashboard").isSubsetOf(METRICAS_DO_CODIGO);
	}

	@Test
	void todoEventoEMetricaDoCodigoApareceNoDashboard() throws IOException {
		String todas = String.join("\n", consultas());

		assertThat(EVENTOS_DO_CODIGO).allSatisfy(evento -> assertThat(todas).contains("FROM " + evento));
		assertThat(METRICAS_DO_CODIGO).allSatisfy(metrica -> assertThat(todas).contains("'" + metrica + "'"));
	}

	private static List<String> consultas() throws IOException {
		JsonNode dashboard = JsonMapper.builder().build().readTree(Files.readString(DASHBOARD));
		List<String> consultas = new ArrayList<>();
		for (JsonNode pagina : dashboard.get("pages")) {
			for (JsonNode widget : pagina.get("widgets")) {
				for (JsonNode consulta : widget.at("/rawConfiguration/nrqlQueries")) {
					consultas.add(consulta.get("query").asString());
				}
			}
		}
		assertThat(consultas).isNotEmpty();
		return consultas;
	}

	private static void coletar(Pattern padrao, String texto, Set<String> destino) {
		Matcher encontrado = padrao.matcher(texto);
		while (encontrado.find()) {
			destino.add(encontrado.group(1));
		}
	}
}
