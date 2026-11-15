package br.com.oficina.os.mensageria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.oficina.os.config.ControllerIntegrationTestSupport;
import br.com.oficina.os.config.DadosDeTeste;
import br.com.oficina.os.config.IntegrationTest;
import br.com.oficina.os.mensageria.config.MensageriaProperties;
import br.com.oficina.os.mensageria.outbox.OutboxEvento;
import br.com.oficina.os.mensageria.outbox.OutboxEventoRepository;
import br.com.oficina.os.mensageria.outbox.OutboxPublicador;
import br.com.oficina.os.mensageria.rabbit.RabbitEventoPublisher;
import br.com.oficina.os.security.JwtService;
import br.com.oficina.os.shared.observabilidade.CorrelationIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Outbox de ponta a ponta, com PostgreSQL e RabbitMQ reais: o evento nasce na mesma transação da
 * OS, é publicado com os headers do contrato e sobrevive a uma falha do broker.
 */
@IntegrationTest
class OutboxIntegrationTest extends ControllerIntegrationTestSupport {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private OutboxPublicador publicador;

	@Autowired
	private OutboxEventoRepository outboxRepository;

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@Autowired
	private AmqpAdmin amqpAdmin;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private MensageriaProperties propriedades;

	@Autowired
	private JsonMapper jsonMapper;

	private String filaEspia;

	@BeforeEach
	void setUp() {
		// Fila temporária ligada a tudo o que o OS Service publica, no papel do Billing e da Execução
		filaEspia = "teste.espia." + UUID.randomUUID();
		amqpAdmin.declareQueue(new Queue(filaEspia, false, false, false));
		Binding binding = BindingBuilder.bind(new Queue(filaEspia))
				.to(new TopicExchange(propriedades.exchangeOs())).with("#");
		amqpAdmin.declareBinding(binding);
		publicarTudo();
		while (rabbitTemplate.receive(filaEspia, 100) != null) {
			// descarta o que outros testes deixaram pendente
		}
	}

	@AfterEach
	void removerFilaEspia() {
		amqpAdmin.deleteQueue(filaEspia);
	}

	@Test
	void topologiaDeveSerDeclaradaComDeadLetter() {
		QueueInformation entrada = amqpAdmin.getQueueInfo(propriedades.filaEntrada());
		QueueInformation dlq = amqpAdmin.getQueueInfo(propriedades.filaDeadLetter());

		assertThat(entrada).isNotNull();
		assertThat(dlq).isNotNull();
		assertThat(amqpAdmin.getQueueProperties(propriedades.filaEntrada())).isNotNull();
	}

	@Test
	void pedidoDeOrcamentoDevePublicarOsEventosEmOrdemComOsHeadersDoContrato() throws Exception {
		String correlationId = "teste-" + UUID.randomUUID();
		long osId = abrirOsComItemEServico(correlationId);
		executar(patch("/ordens/{id}/iniciar-diagnostico", osId), correlationId).andExpect(status().isOk());
		executar(patch("/ordens/{id}/solicitar-aprovacao", osId), correlationId).andExpect(status().isOk());

		List<OutboxEvento> eventos = outboxRepository.findByOrdemServicoIdOrderByIdAsc(osId);
		assertThat(eventos).extracting(OutboxEvento::getTipo).containsExactly(
				"OrdemServicoStatusAlterado", "OrdemServicoStatusAlterado", "SolicitarOrcamento");
		assertThat(eventos).allSatisfy(evento -> {
			assertThat(evento.getCorrelationId()).isEqualTo(correlationId);
			assertThat(evento.getPublicadoEm()).isNull();
		});

		assertThat(publicarTudo()).isGreaterThanOrEqualTo(3);

		List<Message> recebidas = recebidasDaOs(osId, 3);
		assertThat(recebidas).extracting(m -> m.getMessageProperties().getType()).containsExactly(
				"OrdemServicoStatusAlterado", "OrdemServicoStatusAlterado", "SolicitarOrcamento");
		assertThat(recebidas).extracting(m -> m.getMessageProperties().getReceivedRoutingKey()).containsExactly(
				"OrdemServicoStatusAlterado", "OrdemServicoStatusAlterado", "SolicitarOrcamento");

		Message pedido = recebidas.get(2);
		assertThat(pedido.getMessageProperties().getMessageId()).isEqualTo(eventos.get(2).getMessageId().toString());
		assertThat(pedido.getMessageProperties().<String>getHeader(CorrelationIdFilter.CABECALHO)).isEqualTo(correlationId);
		assertThat(pedido.getMessageProperties().getContentType()).isEqualTo("application/json");

		JsonNode envelope = jsonMapper.readTree(new String(pedido.getBody(), StandardCharsets.UTF_8));
		assertThat(envelope.get("tipo").asString()).isEqualTo("SolicitarOrcamento");
		assertThat(envelope.get("ordemServicoId").asLong()).isEqualTo(osId);
		assertThat(envelope.get("correlationId").asString()).isEqualTo(correlationId);
		JsonNode dados = envelope.get("dados");
		assertThat(dados.get("itens")).hasSize(1);
		assertThat(dados.get("servicos")).hasSize(1);
		assertThat(dados.get("itens").get(0).get("quantidade").asInt()).isEqualTo(2);
		assertThat(dados.get("clienteId").asLong()).isPositive();
		assertThat(dados.toString()).as("sem preço nem dados pessoais").doesNotContain("valor", "Cliente Mensageria");

		JsonNode ultimoStatus = jsonMapper.readTree(new String(recebidas.get(1).getBody(), StandardCharsets.UTF_8))
				.get("dados");
		assertThat(ultimoStatus.get("statusAnterior").asString()).isEqualTo("RECEBIDA");
		assertThat(ultimoStatus.get("statusAtual").asString()).isEqualTo("EM_DIAGNOSTICO");
		assertThat(ultimoStatus.get("origem").asString()).isEqualTo("API");

		assertThat(outboxRepository.findByOrdemServicoIdOrderByIdAsc(osId))
				.allSatisfy(evento -> assertThat(evento.getPublicadoEm()).isNotNull());
	}

	@Test
	void seAMudancaNaOsFalhaNenhumEventoEhGravado() throws Exception {
		String correlationId = "teste-" + UUID.randomUUID();
		long osId = abrirOsSemItem(correlationId);
		executar(patch("/ordens/{id}/iniciar-diagnostico", osId), correlationId).andExpect(status().isOk());
		long eventosAntes = outboxRepository.findByOrdemServicoIdOrderByIdAsc(osId).size();

		// Sem item a OS não pode pedir orçamento: a transação é desfeita inteira
		executar(patch("/ordens/{id}/solicitar-aprovacao", osId), correlationId)
				.andExpect(status().isUnprocessableContent());

		assertThat(outboxRepository.findByOrdemServicoIdOrderByIdAsc(osId)).hasSize((int) eventosAntes)
				.extracting(OutboxEvento::getTipo).doesNotContain("SolicitarOrcamento");
	}

	@Test
	void falhaDoBrokerMantemOEventoPendenteNaOrdemAteVoltar() {
		long osId = ThreadLocalRandom.current().nextLong(1_000_000, 2_000_000);
		UUID quebrado = inserirPendente(osId, "exchange.que.nao.existe");
		UUID seguinte = inserirPendente(osId, propriedades.exchangeOs());

		int publicados = publicador.publicarPendentes();

		assertThat(publicados).isZero();
		OutboxEvento falhou = outboxRepository.findByMessageId(quebrado);
		assertThat(falhou.getPublicadoEm()).isNull();
		assertThat(falhou.getTentativas()).isEqualTo(1);
		assertThat(falhou.getUltimoErro()).contains("exchange.que.nao.existe");
		assertThat(outboxRepository.findByMessageId(seguinte).getPublicadoEm())
				.as("o evento seguinte espera, para não chegar antes do anterior").isNull();

		// O broker "volta" (aqui, a exchange passa a existir para o evento) e tudo é publicado em ordem
		jdbcTemplate.update("UPDATE outbox_evento SET exchange = ? WHERE message_id = ?", propriedades.exchangeOs(), quebrado);
		assertThat(publicador.publicarPendentes()).isEqualTo(2);

		assertThat(recebidasDaOs(osId, 2)).extracting(m -> m.getMessageProperties().getMessageId())
				.containsExactly(quebrado.toString(), seguinte.toString());
		assertThat(outboxRepository.countByPublicadoEmIsNull()).isZero();
	}

	private int publicarTudo() {
		int total = 0;
		int rodada;
		do {
			rodada = publicador.publicarPendentes();
			total += rodada;
		} while (rodada > 0);
		return total;
	}

	private List<Message> recebidasDaOs(long osId, int esperadas) {
		List<Message> daOs = new ArrayList<>();
		Message mensagem;
		while (daOs.size() < esperadas && (mensagem = rabbitTemplate.receive(filaEspia, 5_000)) != null) {
			Object os = mensagem.getMessageProperties().getHeader(RabbitEventoPublisher.CABECALHO_ORDEM_SERVICO);
			if (os != null && Long.parseLong(os.toString()) == osId) {
				daOs.add(mensagem);
			}
		}
		return daOs;
	}

	private UUID inserirPendente(long osId, String exchange) {
		UUID messageId = UUID.randomUUID();
		jdbcTemplate.update("""
				INSERT INTO outbox_evento (message_id, tipo, exchange, routing_key, ordem_servico_id, correlation_id, payload)
				VALUES (?, 'OrdemServicoStatusAlterado', ?, 'OrdemServicoStatusAlterado', ?, 'corr-teste', '{}')
				""", messageId, exchange, osId);
		return messageId;
	}

	private long abrirOsComItemEServico(String correlationId) throws Exception {
		long clienteId = criar("/clientes", correlationId, Map.of(
				"nome", "Cliente Mensageria", "cpfCnpj", DadosDeTeste.cpf(), "cep", "01001000",
				"logradouro", "Praça da Sé", "bairro", "Sé", "cidade", "São Paulo", "uf", "SP",
				"data_nascimento", "1990-05-10"));
		long veiculoId = criar("/veiculos", correlationId, Map.of(
				"clienteId", clienteId, "placa", DadosDeTeste.placa(), "marca", "Fiat", "modelo", "Argo", "ano", 2022));
		return criar("/ordens", correlationId, Map.of(
				"descricaoServicosExecutados", "Revisão", "clienteId", clienteId, "veiculoId", veiculoId,
				"servicos", List.of(Map.of("servicoId", 30, "nome", "Troca de óleo")),
				"itens", List.of(Map.of("itemId", 20, "nome", "Filtro", "quantidade", 2))));
	}

	private long abrirOsSemItem(String correlationId) throws Exception {
		long clienteId = criar("/clientes", correlationId, Map.of(
				"nome", "Cliente Mensageria", "cpfCnpj", DadosDeTeste.cpf(), "cep", "01001000",
				"logradouro", "Praça da Sé", "bairro", "Sé", "cidade", "São Paulo", "uf", "SP",
				"data_nascimento", "1990-05-10"));
		long veiculoId = criar("/veiculos", correlationId, Map.of(
				"clienteId", clienteId, "placa", DadosDeTeste.placa(), "marca", "Fiat", "modelo", "Argo", "ano", 2022));
		return criar("/ordens", correlationId, Map.of(
				"descricaoServicosExecutados", "Revisão", "clienteId", clienteId, "veiculoId", veiculoId,
				"servicos", List.of(Map.of("servicoId", 31, "nome", "Alinhamento"))));
	}

	private long criar(String rota, String correlationId, Object corpo) throws Exception {
		String resposta = mockMvc.perform(post(rota)
						.header("Authorization", bearerTokenAdmin(jwtService))
						.header(CorrelationIdFilter.CABECALHO, correlationId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(jsonMapper.writeValueAsString(corpo)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return jsonMapper.readTree(resposta).get("id").asLong();
	}

	private ResultActions executar(MockHttpServletRequestBuilder requisicao, String correlationId) throws Exception {
		return mockMvc.perform(requisicao
				.header("Authorization", bearerTokenAdmin(jwtService))
				.header(CorrelationIdFilter.CABECALHO, correlationId));
	}
}
