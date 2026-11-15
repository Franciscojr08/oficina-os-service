package br.com.oficina.os.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.oficina.os.apoio.OficinaApi;
import br.com.oficina.os.apoio.SimuladorServicosExternos;
import br.com.oficina.os.config.ControllerIntegrationTestSupport;
import br.com.oficina.os.config.IntegrationTest;
import br.com.oficina.os.mensageria.config.MensageriaProperties;
import br.com.oficina.os.mensageria.outbox.OutboxPublicador;
import br.com.oficina.os.saga.application.VerificadorPrazosSaga;
import br.com.oficina.os.security.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.UUID;

/**
 * Saga de ponta a ponta com PostgreSQL e RabbitMQ reais. O {@link SimuladorServicosExternos} faz o
 * papel do Billing e da Execução: lê os comandos que o OS Service publica e responde com eventos.
 * Cobre o caminho feliz (com a aprovação do cliente pela API), as saídas de compensação (orçamento
 * sem resposta, orçamento reprovado, pagamento recusado, execução que falha na fila ou no meio do
 * reparo, com estorno), o cancelamento de OS paga, o prazo esgotado e a mensagem fora de ordem. Os mesmos fluxos, em linguagem de negócio, estão em
 * {@code src/test/resources/features}.
 */
@IntegrationTest
class SagaIntegrationTest extends ControllerIntegrationTestSupport {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private JsonMapper jsonMapper;

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@Autowired
	private AmqpAdmin amqpAdmin;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private MensageriaProperties mensageria;

	@Autowired
	private OutboxPublicador publicador;

	@Autowired
	private VerificadorPrazosSaga verificadorPrazos;

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
	void caminhoFelizDoPedidoDeOrcamentoAteAEntrega() throws Exception {
		long osId = oficina.abrirOsEPedirOrcamento();

		JsonNode pedidoOrcamento = simulador.aguardarComando(osId, "SolicitarOrcamento");
		assertThat(pedidoOrcamento.at("/dados/itens/0/quantidade").asInt()).isEqualTo(2);
		assertThat(pedidoOrcamento.at("/dados/servicos")).hasSize(1);
		assertThat(pedidoOrcamento.get("dados").toString()).as("sem preço, como no contrato").doesNotContain("valor");
		assertSaga(osId, "AGUARDANDO_ORCAMENTO", "EM_ANDAMENTO");
		UUID sagaId = simulador.sagaIdAtual(osId);
		assertThat(pedidoOrcamento.get("sagaId").asString()).isEqualTo(sagaId.toString());
		assertThat(pedidoOrcamento.get("codigoOrdemServico").asString())
				.isEqualTo(jdbcTemplate.queryForObject("SELECT codigo FROM ordem_servico WHERE id = ?", String.class, osId));

		simulador.responderComoBilling(osId, "OrcamentoGerado", Map.of("orcamentoId", 501));
		oficina.aguardarStatus(osId, "AGUARDANDO_APROVACAO");
		assertThat(jdbcTemplate.queryForObject("SELECT orcamento_id FROM ordem_servico WHERE id = ?", Long.class, osId))
				.isEqualTo(501L);

		oficina.aprovar(osId);
		JsonNode cobranca = simulador.aguardarComando(osId, "CobrarPagamento");
		assertThat(cobranca.at("/dados/orcamentoId").asLong()).isEqualTo(501L);
		assertThat(cobranca.get("sagaId").asString()).isEqualTo(sagaId.toString());
		assertSaga(osId, "AGUARDANDO_PAGAMENTO", "EM_ANDAMENTO");
		assertThat(oficina.statusOs(osId)).as("só vira APROVADA com o pagamento confirmado")
				.isEqualTo("AGUARDANDO_APROVACAO");

		String correlationPagamento = simulador.responderComoBilling(osId, "PagamentoConfirmado", Map.of("pagamentoId", 901));
		oficina.aguardarStatus(osId, "APROVADA");
		JsonNode pedidoExecucao = simulador.aguardarComando(osId, "EnfileirarExecucao");
		assertThat(pedidoExecucao.get("sagaId").asString()).as("a mesma Saga acompanha todos os comandos")
				.isEqualTo(sagaId.toString());
		assertThat(pedidoExecucao.at("/dados/servicos/0/nome").asString()).isNotBlank();
		assertThat(pedidoExecucao.at("/dados/itens/0/quantidade").asInt()).isEqualTo(2);
		assertThat(pedidoExecucao.get("correlationId").asString())
				.as("o comando seguinte carrega o correlationId do evento que o provocou")
				.isEqualTo(correlationPagamento);

		simulador.responderComoExecucao(osId, "ExecucaoAguardandoItens", Map.of("itensEmFalta", java.util.List.of(
				Map.of("itemId", 1, "quantidadeNecessaria", 2, "quantidadeDisponivel", 0))));
		oficina.aguardarStatus(osId, "AGUARDANDO_ITENS");
		assertSaga(osId, "AGUARDANDO_ITENS", "EM_ANDAMENTO");

		simulador.responderComoExecucao(osId, "ExecucaoIniciada", Map.of());
		oficina.aguardarStatus(osId, "EM_EXECUCAO");
		simulador.responderComoExecucao(osId, "ServicoFinalizado", Map.of("servicoId", 1, "duracaoMinutos", 40));
		simulador.responderComoExecucao(osId, "ExecucaoFinalizada", Map.of());
		oficina.aguardarStatus(osId, "FINALIZADA");
		assertSaga(osId, "EM_EXECUCAO", "CONCLUIDA");

		oficina.executar(patch("/ordens/{id}/entregar", osId)).andExpect(status().isOk());

		JsonNode historico = oficina.historico(osId);
		assertThat(historico.findValuesAsString("origem")).containsExactly(
				"API", "API", "OrcamentoGerado", "PagamentoConfirmado", "ExecucaoAguardandoItens",
				"ExecucaoIniciada", "ExecucaoFinalizada", "API");
		assertThat(historico.get(historico.size() - 1).get("status").asString()).isEqualTo("ENTREGUE");
	}

	@Test
	void orcamentoSemRespostaDevolveAoDiagnosticoEPermiteNovoPedidoEmOutraSaga() throws Exception {
		long osId = oficina.abrirOsEPedirOrcamento();
		String primeira = simulador.aguardarComando(osId, "SolicitarOrcamento").get("sagaId").asString();
		assertThat(oficina.statusOs(osId)).as("a espera do orçamento não é um status").isEqualTo("EM_DIAGNOSTICO");

		esgotarPrazoDoOrcamento(osId);

		assertThat(oficina.statusOs(osId)).isEqualTo("EM_DIAGNOSTICO");
		assertSaga(osId, "AGUARDANDO_ORCAMENTO", "COMPENSADA");

		// A OS volta a ser editável e pode pedir orçamento de novo: a Saga recomeça com outro id
		oficina.pedirOrcamento(osId);
		assertSaga(osId, "AGUARDANDO_ORCAMENTO", "EM_ANDAMENTO");
		String segunda = simulador.comandos(osId, "SolicitarOrcamento").stream()
				.map(m -> jsonMapper.readTree(new String(m.getBody(), java.nio.charset.StandardCharsets.UTF_8))
						.get("sagaId").asString())
				.reduce((anterior, atual) -> atual).orElseThrow();
		assertThat(segunda).isNotEqualTo(primeira).isEqualTo(simulador.sagaIdAtual(osId).toString());
	}

	@Test
	void naoAlteraAOsEnquantoOOrcamentoEstaSendoGerado() throws Exception {
		long osId = oficina.abrirOsEPedirOrcamento();
		simulador.aguardarComando(osId, "SolicitarOrcamento");

		oficina.executar(patch("/ordens/{id}/solicitar-aprovacao", osId)).andExpect(status().isUnprocessableContent());
		oficina.executar(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.post("/ordens/{id}/itens", osId)
						.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
						.content("{\"itemId\": 1, \"nome\": \"Filtro\", \"quantidade\": 1}"))
				.andExpect(status().isUnprocessableContent());
	}

	@Test
	void orcamentoReprovadoCancelaSemCompensacao() throws Exception {
		long osId = osAguardandoAprovacao();

		oficina.reprovar(osId);

		assertThat(oficina.statusOs(osId)).isEqualTo("CANCELADA");
		assertSaga(osId, "AGUARDANDO_APROVACAO", "CANCELADA");
		assertThat(simulador.comandos(osId, "EstornarPagamento")).isEmpty();
		assertThat(simulador.comandos(osId, "CobrarPagamento")).isEmpty();
	}

	@Test
	void pagamentoRecusadoCancelaSemCompensacao() throws Exception {
		long osId = osAguardandoPagamento();

		simulador.responderComoBilling(osId, "PagamentoRecusado", Map.of("motivo", "Cartão recusado"));

		oficina.aguardarStatus(osId, "CANCELADA");
		assertSaga(osId, "AGUARDANDO_PAGAMENTO", "CANCELADA");
		assertThat(simulador.comandos(osId, "EnfileirarExecucao")).isEmpty();
		assertThat(simulador.comandos(osId, "EstornarPagamento")).isEmpty();
	}

	@Test
	void execucaoFalhouEstornaOPagamentoAntesDeCancelar() throws Exception {
		long osId = osPaga();

		simulador.responderComoExecucao(osId, "ExecucaoFalhou", Map.of("motivo", "Sem estoque do filtro"));

		JsonNode estorno = simulador.aguardarComando(osId, "EstornarPagamento");
		assertThat(estorno.at("/dados/pagamentoId").asLong()).isEqualTo(901L);
		assertThat(estorno.at("/dados/motivo").asString()).isEqualTo("Sem estoque do filtro");
		assertSaga(osId, "AGUARDANDO_ESTORNO", "COMPENSANDO");
		assertThat(oficina.statusOs(osId)).as("a OS só é cancelada com o estorno confirmado").isEqualTo("APROVADA");

		simulador.responderComoBilling(osId, "PagamentoEstornado", Map.of("pagamentoId", 901));

		oficina.aguardarStatus(osId, "CANCELADA");
		assertSaga(osId, "AGUARDANDO_ESTORNO", "COMPENSADA");
	}

	@Test
	void execucaoQueFalhaNoMeioDoReparoEstornaECancela() throws Exception {
		long osId = osPaga();
		simulador.responderComoExecucao(osId, "ExecucaoIniciada", Map.of());
		oficina.aguardarStatus(osId, "EM_EXECUCAO");

		simulador.responderComoExecucao(osId, "ExecucaoFalhou",
				Map.of("motivo", "Peça danificada na montagem", "etapa", "EXECUCAO_SERVICO"));

		JsonNode estorno = simulador.aguardarComando(osId, "EstornarPagamento");
		assertThat(estorno.at("/dados/pagamentoId").asLong()).isEqualTo(901L);
		assertThat(oficina.statusOs(osId)).isEqualTo("EM_EXECUCAO");

		simulador.responderComoBilling(osId, "PagamentoEstornado", Map.of("pagamentoId", 901));
		oficina.aguardarStatus(osId, "CANCELADA");
		assertSaga(osId, "AGUARDANDO_ESTORNO", "COMPENSADA");
	}

	@Test
	void naoCancelaEnquantoACobrancaEstaEmAndamento() throws Exception {
		long osId = osAguardandoPagamento();

		oficina.executar(patch("/ordens/{id}/cancelar", osId)).andExpect(status().isUnprocessableContent());

		assertSaga(osId, "AGUARDANDO_PAGAMENTO", "EM_ANDAMENTO");
		assertThat(oficina.statusOs(osId)).isEqualTo("AGUARDANDO_APROVACAO");
	}

	@Test
	void naoAprovaDuasVezesNemSemOrcamento() throws Exception {
		long osId = osAguardandoAprovacao();
		oficina.aprovar(osId);
		simulador.aguardarComando(osId, "CobrarPagamento");

		oficina.executar(patch("/ordens/{id}/aprovar", osId)).andExpect(status().isUnprocessableContent());
		oficina.executar(patch("/ordens/{id}/reprovar", osId)).andExpect(status().isUnprocessableContent());
		assertThat(simulador.comandos(osId, "CobrarPagamento")).hasSize(1);
	}

	@Test
	void cancelamentoDeOsPagaPelaApiDevolveEstoqueEEstorna() throws Exception {
		long osId = osPaga();

		oficina.executar(patch("/ordens/{id}/cancelar", osId))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.status").value("APROVADA"));

		simulador.aguardarComando(osId, "CancelarExecucao");
		simulador.aguardarComando(osId, "EstornarPagamento");
		assertSaga(osId, "AGUARDANDO_ESTORNO", "COMPENSANDO");

		// Segundo pedido enquanto o estorno não volta
		oficina.executar(patch("/ordens/{id}/cancelar", osId)).andExpect(status().isUnprocessableContent());

		simulador.responderComoBilling(osId, "PagamentoEstornado", Map.of("pagamentoId", 901));
		oficina.aguardarStatus(osId, "CANCELADA");
		assertSaga(osId, "AGUARDANDO_ESTORNO", "COMPENSADA");
	}

	@Test
	void prazoEsgotadoReenviaOMesmoComandoEDepoisCompensa() throws Exception {
		long osId = oficina.abrirOsEPedirOrcamento();
		String messageIdOriginal = simulador.aguardarComando(osId, "SolicitarOrcamento").get("messageId").asString();

		// 1º e 2º vencimentos: reenvio do mesmo comando (o Billing descarta se já tinha recebido)
		for (int tentativa = 1; tentativa <= 2; tentativa++) {
			oficina.vencerPrazo(osId);
			assertThat(verificadorPrazos.verificar()).isGreaterThanOrEqualTo(1);
			int esperados = tentativa + 1;
			await().atMost(SimuladorServicosExternos.ESPERA)
					.until(() -> simulador.comandos(osId, "SolicitarOrcamento").size() == esperados);
			assertThat(oficina.saga(osId)).containsEntry("tentativas", tentativa);
		}
		assertThat(simulador.comandos(osId, "SolicitarOrcamento"))
				.extracting(m -> m.getMessageProperties().getMessageId())
				.containsOnly(messageIdOriginal);

		// 3º vencimento: tentativas esgotadas, a OS volta ao diagnóstico
		oficina.vencerPrazo(osId);
		verificadorPrazos.verificar();

		assertThat(oficina.statusOs(osId)).isEqualTo("EM_DIAGNOSTICO");
		assertSaga(osId, "AGUARDANDO_ORCAMENTO", "COMPENSADA");
		JsonNode historico = oficina.historico(osId);
		assertThat(historico.get(historico.size() - 1).get("origem").asString())
				.isEqualTo(VerificadorPrazosSaga.ORIGEM_PRAZO_ORCAMENTO);
	}

	@Test
	void eventoForaDeOrdemVaiParaADlqSemMexerNaOs() throws Exception {
		long osId = oficina.abrirOsEPedirOrcamento();
		simulador.aguardarComando(osId, "SolicitarOrcamento");

		// Pagamento confirmado antes de existir orçamento aprovado
		String messageId = UUID.randomUUID().toString();
		simulador.publicarComoBilling("PagamentoConfirmado", osId, Map.of("pagamentoId", 1), messageId);

		Message rejeitada = simulador.aguardarNaDlq(messageId);
		assertThat(rejeitada).isNotNull();
		assertThat(oficina.statusOs(osId)).isEqualTo("EM_DIAGNOSTICO");
		assertSaga(osId, "AGUARDANDO_ORCAMENTO", "EM_ANDAMENTO");
	}

	@Test
	void respostaAtrasadaDeUmaSagaAntigaNaoContaminaOCicloNovo() throws Exception {
		long osId = oficina.abrirOsEPedirOrcamento();
		UUID sagaAntiga = simulador.sagaIdAtual(osId);
		simulador.aguardarComando(osId, "SolicitarOrcamento");
		esgotarPrazoDoOrcamento(osId);
		oficina.pedirOrcamento(osId);
		assertThat(simulador.sagaIdAtual(osId)).isNotEqualTo(sagaAntiga);

		// O Billing responde, atrasado, o pedido do ciclo anterior
		String messageId = UUID.randomUUID().toString();
		simulador.publicarComoBillingDeOutraSaga(sagaAntiga, "OrcamentoGerado", osId, Map.of("orcamentoId", 999), messageId);

		assertThat(simulador.aguardarNaDlq(messageId)).isNotNull();
		assertThat(oficina.statusOs(osId)).isEqualTo("EM_DIAGNOSTICO");
		assertSaga(osId, "AGUARDANDO_ORCAMENTO", "EM_ANDAMENTO");
		assertThat(jdbcTemplate.queryForObject("SELECT orcamento_id FROM ordem_servico WHERE id = ?", Long.class, osId))
				.isNull();

		// A resposta do ciclo atual segue sendo aceita
		simulador.responderComoBilling(osId, "OrcamentoGerado", Map.of("orcamentoId", 501));
		oficina.aguardarStatus(osId, "AGUARDANDO_APROVACAO");
	}

	@Test
	void pagamentoConfirmadoRepetidoNaoPedeExecucaoDuasVezes() throws Exception {
		long osId = osAguardandoPagamento();

		String messageId = UUID.randomUUID().toString();
		simulador.publicarComoBilling("PagamentoConfirmado", osId, Map.of("pagamentoId", 901), messageId);
		simulador.publicarComoBilling("PagamentoConfirmado", osId, Map.of("pagamentoId", 901), messageId);
		oficina.aguardarStatus(osId, "APROVADA");
		// Uma mensagem posterior garante que a repetição já passou pelo consumidor
		simulador.responderComoExecucao(osId, "ExecucaoIniciada", Map.of());
		oficina.aguardarStatus(osId, "EM_EXECUCAO");

		assertThat(jdbcTemplate.queryForObject(
				"SELECT count(*) FROM outbox_evento WHERE ordem_servico_id = ? AND tipo = 'EnfileirarExecucao'",
				Integer.class, osId)).isEqualTo(1);
		assertThat(simulador.procurarNaDlq(messageId)).isEmpty();
	}

	@Test
	void eventoSemMessageIdRepetidoNaoPedeExecucaoDuasVezes() throws Exception {
		long osId = osAguardandoPagamento();

		// O Billing do PDF não manda messageId: a repetição é reconhecida por sagaId + tipo + dados
		simulador.responderComoBillingSemMessageId(osId, "PagamentoConfirmado", Map.of("pagamentoId", 901));
		simulador.responderComoBillingSemMessageId(osId, "PagamentoConfirmado", Map.of("pagamentoId", 901));
		oficina.aguardarStatus(osId, "APROVADA");
		// Uma mensagem posterior garante que a repetição já passou pelo consumidor
		simulador.responderComoExecucao(osId, "ExecucaoIniciada", Map.of());
		oficina.aguardarStatus(osId, "EM_EXECUCAO");

		assertThat(jdbcTemplate.queryForObject(
				"SELECT count(*) FROM outbox_evento WHERE ordem_servico_id = ? AND tipo = 'EnfileirarExecucao'",
				Integer.class, osId)).isEqualTo(1);
		assertThat(simulador.procurarNaDlqDaSaga(simulador.sagaIdAtual(osId)))
				.as("a repetição foi descartada como duplicata, não rejeitada").isEmpty();
	}

	private long osAguardandoAprovacao() throws Exception {
		long osId = oficina.abrirOsEPedirOrcamento();
		simulador.aguardarComando(osId, "SolicitarOrcamento");
		simulador.responderComoBilling(osId, "OrcamentoGerado", Map.of("orcamentoId", 501));
		oficina.aguardarStatus(osId, "AGUARDANDO_APROVACAO");
		return osId;
	}

	/** Orçamento aprovado pelo cliente e CobrarPagamento enviado ao Billing. */
	private long osAguardandoPagamento() throws Exception {
		long osId = osAguardandoAprovacao();
		oficina.aprovar(osId);
		simulador.aguardarComando(osId, "CobrarPagamento");
		return osId;
	}

	/** Três vencimentos seguidos: dois reenvios e a compensação. */
	private void esgotarPrazoDoOrcamento(long osId) {
		for (int tentativa = 0; tentativa < 3; tentativa++) {
			oficina.vencerPrazo(osId);
			verificadorPrazos.verificar();
		}
	}

	private long osPaga() throws Exception {
		long osId = osAguardandoPagamento();
		simulador.responderComoBilling(osId, "PagamentoConfirmado", Map.of("pagamentoId", 901));
		oficina.aguardarStatus(osId, "APROVADA");
		simulador.aguardarComando(osId, "EnfileirarExecucao");
		return osId;
	}

	private void assertSaga(long osId, String etapa, String situacao) {
		assertThat(oficina.saga(osId)).containsEntry("etapa", etapa).containsEntry("situacao", situacao);
	}
}
