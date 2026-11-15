package br.com.oficina.os.apoio;

import static org.awaitility.Awaitility.await;

import br.com.oficina.os.mensageria.config.MensageriaProperties;
import br.com.oficina.os.mensageria.outbox.OutboxPublicador;
import br.com.oficina.os.mensageria.rabbit.RabbitEventoPublisher;
import br.com.oficina.os.shared.observabilidade.CorrelationIdFilter;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Faz o papel do Billing e da Execução nos testes: lê os comandos que o OS Service publica em
 * {@code os.eventos} (por uma fila própria) e responde publicando eventos nas exchanges desses
 * serviços, no formato do contrato (docs/mensageria.md).
 *
 * <p>O Billing e a Execução de verdade devolvem o {@code sagaId} que receberam no comando. Aqui o
 * simulador o lê do banco do OS Service (a Saga corrente da OS), o que equivale a ter guardado o id do
 * último comando.</p>
 */
public class SimuladorServicosExternos {

	public static final Duration ESPERA = Duration.ofSeconds(20);

	private final RabbitTemplate rabbitTemplate;
	private final AmqpAdmin amqpAdmin;
	private final OutboxPublicador publicador;
	private final JsonMapper jsonMapper;
	private final MensageriaProperties mensageria;
	private final JdbcTemplate jdbcTemplate;

	private final List<Message> recebidos = new CopyOnWriteArrayList<>();
	private final Map<String, Integer> comandosConferidos = new ConcurrentHashMap<>();
	private String filaComandos;

	public SimuladorServicosExternos(RabbitTemplate rabbitTemplate, AmqpAdmin amqpAdmin, OutboxPublicador publicador,
			JsonMapper jsonMapper, MensageriaProperties mensageria, JdbcTemplate jdbcTemplate) {
		this.rabbitTemplate = rabbitTemplate;
		this.amqpAdmin = amqpAdmin;
		this.publicador = publicador;
		this.jsonMapper = jsonMapper;
		this.mensageria = mensageria;
		this.jdbcTemplate = jdbcTemplate;
	}

	/** Cria a fila que recebe tudo o que o OS Service publica. Chamar antes de cada teste. */
	public void iniciar() {
		recebidos.clear();
		comandosConferidos.clear();
		filaComandos = "teste.simulador." + UUID.randomUUID();
		amqpAdmin.declareQueue(new Queue(filaComandos, false, false, false));
		amqpAdmin.declareBinding(BindingBuilder.bind(new Queue(filaComandos))
				.to(new TopicExchange(mensageria.exchangeOs())).with("#"));
	}

	public void encerrar() {
		if (filaComandos != null) {
			amqpAdmin.deleteQueue(filaComandos);
		}
	}

	/** Publica um evento do Billing e devolve o correlationId usado. */
	public String responderComoBilling(long osId, String tipo, Map<String, Object> dados) {
		return publicar(mensageria.exchangeBilling(), tipo, osId, dados, UUID.randomUUID().toString());
	}

	/** Publica um evento da Execução e devolve o correlationId usado. */
	public String responderComoExecucao(long osId, String tipo, Map<String, Object> dados) {
		return publicar(mensageria.exchangeExecucao(), tipo, osId, dados, UUID.randomUUID().toString());
	}

	/** Evento do Billing sem {@code messageId}, como o contrato da Fase 4 permite. */
	public String responderComoBillingSemMessageId(long osId, String tipo, Map<String, Object> dados) {
		return publicar(mensageria.exchangeBilling(), tipo, osId, dados, null, Map.of());
	}

	/** Id da Saga corrente da OS, o que os outros serviços receberam no último comando. */
	public UUID sagaIdAtual(long osId) {
		List<UUID> ids = jdbcTemplate.queryForList(
				"SELECT saga_id FROM saga_ordem_servico WHERE ordem_servico_id = ?", UUID.class, osId);
		return ids.isEmpty() ? UUID.randomUUID() : ids.get(0);
	}

	public String publicarComoBilling(String tipo, long osId, Map<String, Object> dados, String messageId) {
		return publicar(mensageria.exchangeBilling(), tipo, osId, dados, messageId);
	}

	/** Evento do Billing com cabeçalhos extras (por exemplo, o traceparent do trace do Billing). */
	public String responderComoBilling(long osId, String tipo, Map<String, Object> dados, Map<String, String> cabecalhos) {
		return publicar(mensageria.exchangeBilling(), tipo, osId, dados, UUID.randomUUID().toString(), cabecalhos);
	}

	private String publicar(String exchange, String tipo, long osId, Map<String, Object> dados, String messageId) {
		return publicar(exchange, tipo, osId, dados, messageId, Map.of());
	}

	/** Evento do Billing que carrega o sagaId de outro ciclo, como uma resposta atrasada de uma Saga antiga. */
	public String publicarComoBillingDeOutraSaga(UUID sagaId, String tipo, long osId, Map<String, Object> dados,
			String messageId) {
		return publicar(mensageria.exchangeBilling(), tipo, osId, dados, messageId, Map.of(), sagaId);
	}

	private String publicar(String exchange, String tipo, long osId, Map<String, Object> dados, String messageId,
			Map<String, String> cabecalhos) {
		return publicar(exchange, tipo, osId, dados, messageId, cabecalhos, sagaIdAtual(osId));
	}

	private String publicar(String exchange, String tipo, long osId, Map<String, Object> dados, String messageId,
			Map<String, String> cabecalhos, UUID sagaId) {
		String correlationId = "simulado-" + UUID.randomUUID();
		Map<String, Object> envelope = new HashMap<>(Map.of(
				"tipo", tipo, "sagaId", sagaId.toString(), "ordemServicoId", osId,
				"correlationId", correlationId, "ocorridoEm", "2026-11-06T10:00:00Z", "versao", 1, "dados", dados));
		codigoDaOs(osId).ifPresent(codigo -> envelope.put("codigoOrdemServico", codigo));
		if (messageId != null) {
			envelope.put("messageId", messageId);
		}
		MessageBuilder construtor = MessageBuilder.withBody(jsonMapper.writeValueAsBytes(envelope));
		cabecalhos.forEach(construtor::setHeader);
		Message mensagem = construtor
				.setContentType(MessageProperties.CONTENT_TYPE_JSON)
				.setMessageId(messageId)
				.setType(tipo)
				.setHeader(CorrelationIdFilter.CABECALHO, correlationId)
				.build();
		rabbitTemplate.send(exchange, tipo, mensagem);
		return correlationId;
	}

	private Optional<String> codigoDaOs(long osId) {
		return jdbcTemplate.queryForList("SELECT codigo FROM ordem_servico WHERE id = ?", String.class, osId)
				.stream().findFirst();
	}

	/** Publica o outbox e devolve o próximo comando {@code tipo} da OS que ainda não foi conferido. */
	public JsonNode aguardarComando(long osId, String tipo) {
		String chave = osId + ":" + tipo;
		int conferidos = comandosConferidos.getOrDefault(chave, 0);
		await().atMost(ESPERA).until(() -> comandos(osId, tipo).size() > conferidos);
		comandosConferidos.put(chave, conferidos + 1);
		Message comando = comandos(osId, tipo).get(conferidos);
		return jsonMapper.readTree(new String(comando.getBody(), StandardCharsets.UTF_8));
	}

	/** Todos os comandos {@code tipo} da OS publicados até agora (publica o outbox antes de ler). */
	public List<Message> comandos(long osId, String tipo) {
		int rodada;
		do {
			rodada = publicador.publicarPendentes();
		} while (rodada > 0);
		Message mensagem;
		while ((mensagem = rabbitTemplate.receive(filaComandos, 50)) != null) {
			recebidos.add(mensagem);
		}
		return recebidos.stream()
				.filter(m -> tipo.equals(m.getMessageProperties().getType()))
				.filter(m -> String.valueOf(osId).equals(String.valueOf(
						m.getMessageProperties().<Object>getHeader(RabbitEventoPublisher.CABECALHO_ORDEM_SERVICO))))
				.toList();
	}

	/** Espera a mensagem chegar à DLQ do OS Service. */
	public Message aguardarNaDlq(String messageId) {
		long limite = System.currentTimeMillis() + ESPERA.toMillis();
		while (System.currentTimeMillis() < limite) {
			Message mensagem = rabbitTemplate.receive(mensageria.filaDeadLetter(), 500);
			if (mensagem != null && messageId.equals(mensagem.getMessageProperties().getMessageId())) {
				return mensagem;
			}
		}
		return null;
	}

	/** Procura na DLQ uma mensagem da Saga (pelo {@code sagaId} no corpo), sem esperar novas chegadas. */
	public Optional<Message> procurarNaDlqDaSaga(UUID sagaId) {
		Message mensagem;
		while ((mensagem = rabbitTemplate.receive(mensageria.filaDeadLetter(), 200)) != null) {
			if (new String(mensagem.getBody(), StandardCharsets.UTF_8).contains(sagaId.toString())) {
				return Optional.of(mensagem);
			}
		}
		return Optional.empty();
	}

	/** Procura a mensagem no que já está na DLQ, sem esperar novas chegadas. */
	public Optional<Message> procurarNaDlq(String messageId) {
		Message mensagem;
		while ((mensagem = rabbitTemplate.receive(mensageria.filaDeadLetter(), 200)) != null) {
			if (messageId.equals(mensagem.getMessageProperties().getMessageId())) {
				return Optional.of(mensagem);
			}
		}
		return Optional.empty();
	}
}
