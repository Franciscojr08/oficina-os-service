package br.com.oficina.os.bdd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.oficina.os.apoio.OficinaApi;
import br.com.oficina.os.apoio.SimuladorServicosExternos;
import br.com.oficina.os.config.ControllerIntegrationTestSupport;
import br.com.oficina.os.mensageria.config.MensageriaProperties;
import br.com.oficina.os.mensageria.outbox.OutboxPublicador;
import br.com.oficina.os.ordemservico.application.StatusOrdemServico;
import br.com.oficina.os.saga.domain.SituacaoSaga;
import br.com.oficina.os.security.JwtService;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.pt.Dado;
import io.cucumber.java.pt.E;
import io.cucumber.java.pt.Entao;
import io.cucumber.java.pt.Quando;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Passos dos cenários da Saga. O atendente usa a API do OS Service; o Billing e a Execução são
 * simulados pelo {@link SimuladorServicosExternos}, que lê os comandos publicados e responde com
 * eventos pelo RabbitMQ. Uma instância por cenário (escopo de glue do Cucumber).
 */
public class SagaOrdemServicoSteps extends ControllerIntegrationTestSupport {

	private static final Map<String, SituacaoSaga> SITUACOES = Map.of(
			"concluída", SituacaoSaga.CONCLUIDA,
			"cancelada", SituacaoSaga.CANCELADA,
			"compensada", SituacaoSaga.COMPENSADA);

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private JsonMapper jsonMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private DataSource dataSource;

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
	private long osId;
	private long orcamentoId;
	private int quantidadeDoItem;

	@Before
	public void prepararCenario() {
		// Os cenários não passam pelo @Sql da classe base: o usuário do token é criado aqui
		new ResourceDatabasePopulator(new ClassPathResource("db/testdata/admin-user.sql")).execute(dataSource);
		oficina = new OficinaApi(mockMvc, jsonMapper, jdbcTemplate, bearerTokenAdmin(jwtService));
		simulador = new SimuladorServicosExternos(rabbitTemplate, amqpAdmin, publicador, jsonMapper, mensageria,
				jdbcTemplate);
		simulador.iniciar();
	}

	@After
	public void encerrarCenario() {
		simulador.encerrar();
	}

	// ----- atendente (API do OS Service) -----

	@Dado("uma ordem de serviço em diagnóstico com {int} unidades de {string} e o serviço {string}")
	public void umaOrdemDeServicoEmDiagnostico(int quantidade, String item, String servico) throws Exception {
		quantidadeDoItem = quantidade;
		osId = oficina.abrirOsDiagnosticada(item, quantidade, servico);
	}

	@Quando("o atendente conclui o diagnóstico e pede o orçamento")
	public void oAtendentePedeOOrcamento() throws Exception {
		oficina.pedirOrcamento(osId);
	}

	@Quando("o atendente entrega o veículo")
	public void oAtendenteEntregaOVeiculo() throws Exception {
		oficina.executar(patch("/ordens/{id}/entregar", osId)).andExpect(status().isOk());
	}

	// ----- Billing (simulado) -----

	@Entao("o Billing recebe o pedido de orçamento com {int} serviço e {int} unidades do item, sem preço")
	public void oBillingRecebeOPedidoDeOrcamento(int servicos, int quantidade) {
		JsonNode pedido = simulador.aguardarComando(osId, "SolicitarOrcamento");
		assertThat(pedido.at("/dados/servicos")).hasSize(servicos);
		assertThat(pedido.at("/dados/itens/0/quantidade").asInt()).isEqualTo(quantidade);
		assertThat(pedido.at("/dados/clienteId").asLong()).isPositive();
		assertThat(pedido.at("/dados/veiculoId").asLong()).isPositive();
		assertThat(pedido.get("dados").toString()).as("o OS Service não guarda nem envia preço").doesNotContain("valor");
	}

	@Quando("o Billing gera o orçamento {long}")
	public void oBillingGeraOOrcamento(long orcamento) {
		orcamentoId = orcamento;
		simulador.responderComoBilling(osId, "OrcamentoGerado", Map.of("orcamentoId", orcamento));
	}

	@Dado("que o Billing gerou o orçamento {long}")
	public void queOBillingGerouOOrcamento(long orcamento) {
		oBillingGeraOOrcamento(orcamento);
		oficina.aguardarStatus(osId, StatusOrdemServico.AGUARDANDO_APROVACAO.name());
	}

	// ----- cliente (API do OS Service) -----

	@Quando("o cliente aprova o orçamento")
	public void oClienteAprovaOOrcamento() throws Exception {
		oficina.aprovar(osId);
	}

	@Quando("o cliente reprova o orçamento")
	public void oClienteReprovaOOrcamento() throws Exception {
		oficina.reprovar(osId);
	}

	@Entao("o Billing recebe o pedido de cobrança do orçamento {long}")
	public void oBillingRecebeOPedidoDeCobranca(long orcamento) {
		JsonNode cobranca = simulador.aguardarComando(osId, "CobrarPagamento");
		assertThat(cobranca.at("/dados/orcamentoId").asLong()).isEqualTo(orcamento);
	}

	@E("nenhum pedido de cobrança é enviado ao Billing")
	public void nenhumPedidoDeCobranca() {
		assertThat(simulador.comandos(osId, "CobrarPagamento")).isEmpty();
	}

	@Quando("o Billing confirma o pagamento {long}")
	public void oBillingConfirmaOPagamento(long pagamento) {
		simulador.responderComoBilling(osId, "PagamentoConfirmado", Map.of("pagamentoId", pagamento));
	}

	@Dado("que o cliente aprovou e pagou o orçamento com o pagamento {long}")
	public void queOClienteAprovouEPagou(long pagamento) throws Exception {
		queOBillingGerouOOrcamento(500);
		oClienteAprovaOOrcamento();
		simulador.aguardarComando(osId, "CobrarPagamento");
		oBillingConfirmaOPagamento(pagamento);
		oficina.aguardarStatus(osId, StatusOrdemServico.APROVADA.name());
		simulador.aguardarComando(osId, "EnfileirarExecucao");
	}

	@Entao("o Billing recebe o pedido de estorno do pagamento {long} com o motivo {string}")
	public void oBillingRecebeOPedidoDeEstorno(long pagamento, String motivo) {
		JsonNode estorno = simulador.aguardarComando(osId, "EstornarPagamento");
		assertThat(estorno.at("/dados/pagamentoId").asLong()).isEqualTo(pagamento);
		assertThat(estorno.at("/dados/motivo").asString()).isEqualTo(motivo);
	}

	@Quando("o Billing confirma o estorno do pagamento {long}")
	public void oBillingConfirmaOEstorno(long pagamento) {
		simulador.responderComoBilling(osId, "PagamentoEstornado", Map.of("pagamentoId", pagamento));
	}

	@E("nenhum pedido de estorno é enviado ao Billing")
	public void nenhumPedidoDeEstorno() {
		assertThat(simulador.comandos(osId, "EstornarPagamento")).isEmpty();
	}

	// ----- Execução (simulada) -----

	@Entao("a Execução recebe o pedido de reparo com {int} unidades do item")
	public void aExecucaoRecebeOPedidoDeReparo(int quantidade) {
		JsonNode pedido = simulador.aguardarComando(osId, "EnfileirarExecucao");
		assertThat(pedido.at("/dados/itens/0/quantidade").asInt()).isEqualTo(quantidade).isEqualTo(quantidadeDoItem);
	}

	@Quando("a Execução avisa que falta peça no estoque")
	public void aExecucaoAvisaQueFaltaPeca() {
		simulador.responderComoExecucao(osId, "ExecucaoAguardandoItens", Map.of("itensEmFalta", List.of(
				Map.of("itemId", 1, "quantidadeNecessaria", quantidadeDoItem, "quantidadeDisponivel", 0))));
	}

	@Quando("a Execução finaliza o serviço")
	public void aExecucaoFinalizaOServico() {
		simulador.responderComoExecucao(osId, "ServicoFinalizado", Map.of("servicoId", 1, "duracaoMinutos", 40));
	}

	@Quando("a Execução inicia o reparo")
	public void aExecucaoIniciaOReparo() {
		simulador.responderComoExecucao(osId, "ExecucaoIniciada", Map.of());
	}

	@Quando("a Execução finaliza o reparo")
	public void aExecucaoFinalizaOReparo() {
		simulador.responderComoExecucao(osId, "ExecucaoFinalizada", Map.of());
	}

	@Quando("a Execução falha por {string}")
	public void aExecucaoFalha(String motivo) {
		simulador.responderComoExecucao(osId, "ExecucaoFalhou", Map.of("motivo", motivo));
	}

	@E("nenhum pedido de reparo é enviado à Execução")
	public void nenhumPedidoDeReparo() {
		assertThat(simulador.comandos(osId, "EnfileirarExecucao")).isEmpty();
	}

	// ----- estado da OS e da Saga -----

	@Entao("a ordem de serviço fica {string}")
	public void aOrdemDeServicoFica(String descricaoStatus) {
		oficina.aguardarStatus(osId, statusPelaDescricao(descricaoStatus).name());
	}

	@E("a ordem de serviço continua {string} enquanto o estorno não é confirmado")
	public void aOrdemDeServicoContinuaEnquantoOEstornoNaoEConfirmado(String descricaoStatus) {
		oficina.aguardarSituacaoSaga(osId, SituacaoSaga.COMPENSANDO.name());
		assertThat(oficina.statusOs(osId)).isEqualTo(statusPelaDescricao(descricaoStatus).name());
	}

	@E("a ordem de serviço continua {string} enquanto o orçamento é gerado")
	public void aOrdemDeServicoContinuaEnquantoOOrcamentoEGerado(String descricaoStatus) {
		assertThat(oficina.statusOs(osId)).isEqualTo(statusPelaDescricao(descricaoStatus).name());
		assertThat(oficina.saga(osId)).containsEntry("etapa", "AGUARDANDO_ORCAMENTO");
	}

	@E("a ordem de serviço continua {string} enquanto o pagamento não é confirmado")
	public void aOrdemDeServicoContinuaEnquantoOPagamentoNaoEConfirmado(String descricaoStatus) {
		assertThat(oficina.statusOs(osId)).isEqualTo(statusPelaDescricao(descricaoStatus).name());
		assertThat(oficina.saga(osId)).containsEntry("etapa", "AGUARDANDO_PAGAMENTO");
	}

	@E("a Saga termina como {string}")
	public void aSagaTerminaComo(String situacao) {
		SituacaoSaga esperada = SITUACOES.get(situacao);
		assertThat(esperada).as("situação da Saga desconhecida no cenário: %s", situacao).isNotNull();
		oficina.aguardarSituacaoSaga(osId, esperada.name());
	}

	@E("o histórico da ordem de serviço é:")
	public void oHistoricoDaOrdemDeServicoE(DataTable esperado) throws Exception {
		List<Map<String, String>> historico = new ArrayList<>();
		for (JsonNode mudanca : oficina.historico(osId)) {
			historico.add(Map.of(
					"status", mudanca.get("descricao").asString(),
					"origem", mudanca.get("origem").asString()));
		}
		assertThat(historico).containsExactlyElementsOf(esperado.asMaps());
	}

	private static StatusOrdemServico statusPelaDescricao(String descricao) {
		return Arrays.stream(StatusOrdemServico.values())
				.filter(status -> status.getDescricao().equalsIgnoreCase(descricao))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Status desconhecido no cenário: " + descricao));
	}
}
