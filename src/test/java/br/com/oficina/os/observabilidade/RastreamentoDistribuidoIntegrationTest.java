package br.com.oficina.os.observabilidade;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.oficina.os.apoio.OficinaApi;
import br.com.oficina.os.apoio.SimuladorServicosExternos;
import br.com.oficina.os.config.ControllerIntegrationTestSupport;
import br.com.oficina.os.config.IntegrationTest;
import br.com.oficina.os.mensageria.config.MensageriaProperties;
import br.com.oficina.os.mensageria.outbox.OutboxPublicador;
import br.com.oficina.os.saga.application.SagaObservabilidade;
import br.com.oficina.os.security.JwtService;
import br.com.oficina.os.shared.observabilidade.TelemetriaEmMemoria;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

/**
 * Rastreamento distribuído de ponta a ponta, com PostgreSQL e RabbitMQ reais. A telemetria é a
 * única peça simulada e se comporta como o agent do New Relic: o trace da transação atual é o que
 * sai nos cabeçalhos, e aceitar um trace recebido faz a transação passar a fazer parte dele.
 *
 * <p>Mostra que o outbox não quebra o trace (o publicador roda depois, em outra transação) e que o
 * trace segue pelos três serviços: requisição HTTP → SolicitarOrcamento (Billing) → resposta do
 * Billing → EnfileirarExecucao (Execução), todos no mesmo trace.</p>
 */
@IntegrationTest
@Import(RastreamentoDistribuidoIntegrationTest.Configuracao.class)
class RastreamentoDistribuidoIntegrationTest extends ControllerIntegrationTestSupport {

	private static final String TRACE_DA_REQUISICAO = "00-11111111111111111111111111111111-aaaaaaaaaaaaaaaa-01";
	private static final String TRACE_DO_BILLING = "00-22222222222222222222222222222222-bbbbbbbbbbbbbbbb-01";

	@Autowired
	private TelemetriaEmMemoria telemetria;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private JsonMapper jsonMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@Autowired
	private AmqpAdmin amqpAdmin;

	@Autowired
	private OutboxPublicador publicador;

	@Autowired
	private MensageriaProperties mensageria;

	private SimuladorServicosExternos simulador;
	private OficinaApi oficina;

	@BeforeEach
	void setUp() {
		simulador = new SimuladorServicosExternos(rabbitTemplate, amqpAdmin, publicador, jsonMapper, mensageria,
				jdbcTemplate);
		simulador.iniciar();
		oficina = new OficinaApi(mockMvc, jsonMapper, jdbcTemplate, bearerTokenAdmin(jwtService));
	}

	@AfterEach
	void tearDown() {
		simulador.encerrar();
	}

	@Test
	void oTraceDaRequisicaoSeguePeloBillingEChegaAExecucao() throws Exception {
		// Requisição HTTP do atendente, dentro do trace da requisição
		telemetria.traceAtual(Map.of("traceparent", TRACE_DA_REQUISICAO, "newrelic", "nr-requisicao"));
		long osId = oficina.abrirOsEPedirOrcamento();

		// O publicador roda em outra transação, sem trace próprio
		telemetria.traceAtual(Map.of());
		simulador.aguardarComando(osId, "SolicitarOrcamento");
		Message pedidoOrcamento = simulador.comandos(osId, "SolicitarOrcamento").get(0);
		assertThat(pedidoOrcamento.getMessageProperties().<String>getHeader("traceparent"))
				.as("o pedido de orçamento sai no trace da requisição, não no do publicador")
				.isEqualTo(TRACE_DA_REQUISICAO);
		assertThat(pedidoOrcamento.getMessageProperties().<String>getHeader("newrelic")).isEqualTo("nr-requisicao");

		// O Billing responde no trace dele (que continua o da requisição do lado de lá)
		simulador.responderComoBilling(osId, "OrcamentoGerado", Map.of("orcamentoId", 1), Map.of());
		oficina.aguardarStatus(osId, "AGUARDANDO_APROVACAO");
		oficina.aprovar(osId);
		simulador.aguardarComando(osId, "CobrarPagamento");
		simulador.responderComoBilling(osId, "PagamentoConfirmado", Map.of("pagamentoId", 9),
				Map.of("traceparent", TRACE_DO_BILLING, "tracestate", "nr=billing"));
		oficina.aguardarStatus(osId, "APROVADA");

		assertThat(telemetria.rastreamentosContinuados())
				.as("o consumidor aceita o trace que veio do Billing")
				.contains(Map.of("traceparent", TRACE_DO_BILLING, "tracestate", "nr=billing"));
		assertThat(telemetria.transacoes()).contains("Mensagem/PagamentoConfirmado");

		// O comando para a Execução, publicado depois, segue no mesmo trace
		telemetria.traceAtual(Map.of());
		simulador.aguardarComando(osId, "EnfileirarExecucao");
		Message pedidoExecucao = simulador.comandos(osId, "EnfileirarExecucao").get(0);
		assertThat(pedidoExecucao.getMessageProperties().<String>getHeader("traceparent")).isEqualTo(TRACE_DO_BILLING);
		assertThat(pedidoExecucao.getMessageProperties().<String>getHeader("tracestate")).isEqualTo("nr=billing");
	}

	@Test
	void aSagaGeraEventosDeNegocioDoInicioAoFimComADuracao() throws Exception {
		long osId = oficina.abrirOsEPedirOrcamento();
		simulador.aguardarComando(osId, "SolicitarOrcamento");
		simulador.responderComoBilling(osId, "OrcamentoGerado", Map.of("orcamentoId", 1));
		oficina.aguardarStatus(osId, "AGUARDANDO_APROVACAO");

		oficina.reprovar(osId);

		List<Map<String, Object>> transicoes = telemetria.eventos(SagaObservabilidade.EVENTO_TRANSICAO).stream()
				.map(TelemetriaEmMemoria.Evento::atributos)
				.filter(atributos -> Long.valueOf(osId).equals(atributos.get("ordemServicoId")))
				.toList();
		assertThat(transicoes).extracting(atributos -> atributos.get("gatilho"))
				.containsExactly("SolicitarOrcamento", "OrcamentoGerado", "ReprovacaoApi");
		assertThat(transicoes.get(2))
				.containsEntry("situacao", "CANCELADA")
				.containsEntry("encerrada", true)
				.containsKey("duracaoMs");
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class Configuracao {

		@Bean
		@Primary
		TelemetriaEmMemoria telemetriaEmMemoria() {
			return new TelemetriaEmMemoria();
		}
	}
}
