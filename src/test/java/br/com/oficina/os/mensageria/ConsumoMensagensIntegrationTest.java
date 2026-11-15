package br.com.oficina.os.mensageria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import br.com.oficina.os.config.IntegrationTest;
import br.com.oficina.os.mensageria.config.MensageriaProperties;
import br.com.oficina.os.mensageria.consumo.ChaveDeduplicacao;
import br.com.oficina.os.mensageria.consumo.EventoProcessadoRepository;
import br.com.oficina.os.mensageria.consumo.ManipuladorEvento;
import br.com.oficina.os.mensageria.evento.EnvelopeMensagem;
import br.com.oficina.os.shared.exception.RegraNegocioException;
import br.com.oficina.os.shared.observabilidade.CorrelationIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Consumo idempotente com RabbitMQ e PostgreSQL reais, isolado da Saga: o Billing é simulado
 * publicando na exchange dele (com uma routing key ligada à fila) e o envelope leva um tipo só de
 * teste, tratado por um manipulador que conta chamadas e simula falhas.
 */
@IntegrationTest
@Import(ConsumoMensagensIntegrationTest.Configuracao.class)
class ConsumoMensagensIntegrationTest {

	private static final Duration ESPERA = Duration.ofSeconds(15);

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@Autowired
	private MensageriaProperties propriedades;

	@Autowired
	private EventoProcessadoRepository eventoProcessadoRepository;

	@Autowired
	private JsonMapper jsonMapper;

	@Autowired
	private ManipuladorDeTeste manipulador;

	@BeforeEach
	void esvaziarDlq() {
		while (rabbitTemplate.receive(propriedades.filaDeadLetter(), 100) != null) {
			// cada teste começa com a DLQ vazia
		}
	}

	@Test
	void devePublicarConsumirEIgnorarAMensagemDuplicada() {
		UUID messageId = UUID.randomUUID();
		String corpo = envelope(messageId, "EventoDeTeste", "ok", "corr-dup");

		publicarComoBilling("OrcamentoGerado", corpo);
		await().atMost(ESPERA).until(() -> manipulador.chamadas(messageId) == 1);

		// Reentrega da mesma mensagem (mesmo messageId), como o broker faz em caso de falha de rede
		publicarComoBilling("OrcamentoGerado", corpo);
		// Mensagem seguinte: com um único consumidor, quando ela for processada a duplicata já passou
		UUID marcador = UUID.randomUUID();
		publicarComoBilling("OrcamentoGerado", envelope(marcador, "EventoDeTeste", "ok", "corr-marcador"));
		await().atMost(ESPERA).until(() -> manipulador.chamadas(marcador) == 1);

		assertThat(manipulador.chamadas(messageId)).as("a duplicata não pode ter efeito").isEqualTo(1);
		assertThat(eventoProcessadoRepository.foiProcessado(messageId)).isTrue();
		assertThat(rabbitTemplate.receive(propriedades.filaDeadLetter(), 500)).as("duplicata não é erro").isNull();
	}

	@Test
	void correlationIdDaMensagemDeveChegarAoManipulador() {
		UUID messageId = UUID.randomUUID();

		publicarComoBilling("OrcamentoGerado", envelope(messageId, "EventoDeTeste", "ok", "corr-propagado"));

		await().atMost(ESPERA).until(() -> manipulador.chamadas(messageId) == 1);
		assertThat(manipulador.correlationId(messageId)).isEqualTo("corr-propagado");
	}

	@Test
	void jsonInvalidoVaiParaADlqSemDerrubarOConsumidor() {
		publicarComoBilling("OrcamentoGerado", "{ isto não é json");

		Message rejeitada = rabbitTemplate.receive(propriedades.filaDeadLetter(), ESPERA.toMillis());
		assertThat(rejeitada).isNotNull();
		assertThat(new String(rejeitada.getBody(), StandardCharsets.UTF_8)).isEqualTo("{ isto não é json");

		// O consumidor continua vivo
		UUID seguinte = UUID.randomUUID();
		publicarComoBilling("OrcamentoGerado", envelope(seguinte, "EventoDeTeste", "ok", "corr"));
		await().atMost(ESPERA).until(() -> manipulador.chamadas(seguinte) == 1);
	}

	@Test
	void eventoSemManipuladorVaiParaADlq() {
		UUID messageId = UUID.randomUUID();

		// A routing key está ligada à fila, mas ninguém trata o tipo do envelope
		publicarComoExecucao("ExecucaoIniciada", envelope(messageId, "TipoDesconhecido", "ok", "corr"));

		Message rejeitada = rabbitTemplate.receive(propriedades.filaDeadLetter(), ESPERA.toMillis());
		assertThat(rejeitada).isNotNull();
		assertThat(rejeitada.getMessageProperties().getMessageId()).isEqualTo(messageId.toString());
		assertThat(eventoProcessadoRepository.foiProcessado(messageId)).isFalse();
	}

	@Test
	void eventoQueContradizOEstadoDaOsVaiParaADlqSemNovaTentativaENaoFicaRegistrado() {
		UUID messageId = UUID.randomUUID();

		publicarComoBilling("OrcamentoGerado", envelope(messageId, "EventoDeTeste", "regra", "corr"));

		Message rejeitada = rabbitTemplate.receive(propriedades.filaDeadLetter(), ESPERA.toMillis());
		assertThat(rejeitada).isNotNull();
		assertThat(rejeitada.getMessageProperties().getMessageId()).isEqualTo(messageId.toString());
		assertThat(manipulador.chamadas(messageId)).as("regra de domínio não melhora tentando de novo").isEqualTo(1);
		assertThat(eventoProcessadoRepository.foiProcessado(messageId))
				.as("a transação é desfeita: a mensagem pode ser reprocessada a partir da DLQ").isFalse();
	}

	@Test
	void falhaTransitoriaEhTentadaDeNovoEProcessadaUmaVez() {
		UUID messageId = UUID.randomUUID();

		publicarComoBilling("OrcamentoGerado", envelope(messageId, "EventoDeTeste", "falhar:2", "corr"));

		await().atMost(ESPERA).until(() -> eventoProcessadoRepository.foiProcessado(messageId));
		assertThat(manipulador.chamadas(messageId)).isEqualTo(3);
		assertThat(rabbitTemplate.receive(propriedades.filaDeadLetter(), 500)).isNull();
	}

	@Test
	void falhaQuePersisteVaiParaADlqDepoisDasTentativas() {
		UUID messageId = UUID.randomUUID();

		publicarComoBilling("OrcamentoGerado", envelope(messageId, "EventoDeTeste", "falhar:10", "corr"));

		Message rejeitada = rabbitTemplate.receive(propriedades.filaDeadLetter(), ESPERA.toMillis());
		assertThat(rejeitada).isNotNull();
		assertThat(rejeitada.getMessageProperties().getMessageId()).isEqualTo(messageId.toString());
		assertThat(manipulador.chamadas(messageId)).isEqualTo(3);
		assertThat(eventoProcessadoRepository.foiProcessado(messageId)).isFalse();
	}

	@Test
	void mensagemSemMessageIdEhDeduplicadaPorSagaTipoEDados() {
		UUID saga = UUID.randomUUID();
		String marca = "sem-id-" + UUID.randomUUID();
		String corpo = corpo(null, saga, "EventoDeTeste", "ok", marca, "corr-sem-id");

		publicarComoBilling("OrcamentoGerado", corpo);
		await().atMost(ESPERA).until(() -> manipulador.chamadas(marca) == 1);

		// Reentrega da mesma mensagem, que não tem messageId para o consumidor reconhecer
		publicarComoBilling("OrcamentoGerado", corpo);
		// Outra mensagem da mesma Saga e do mesmo tipo, com dados diferentes (outro serviço finalizado, por
		// exemplo): é um fato novo e precisa ser processada
		String outra = "sem-id-outra-" + UUID.randomUUID();
		publicarComoBilling("OrcamentoGerado", corpo(null, saga, "EventoDeTeste", "ok", outra, "corr"));
		await().atMost(ESPERA).until(() -> manipulador.chamadas(outra) == 1);

		assertThat(manipulador.chamadas(marca)).as("a repetição não pode ter efeito").isEqualTo(1);
		assertThat(eventoProcessadoRepository.foiProcessado(
				ChaveDeduplicacao.de(jsonMapper.readValue(corpo, EnvelopeMensagem.class)))).isTrue();
		assertThat(rabbitTemplate.receive(propriedades.filaDeadLetter(), 500)).as("duplicata não é erro").isNull();
	}

	@Test
	void mensagemSemSagaIdVaiParaADlqSemSerProcessada() {
		UUID messageId = UUID.randomUUID();

		publicarComoBilling("OrcamentoGerado",
				corpo(messageId.toString(), null, "EventoDeTeste", "ok", messageId.toString(), "corr"));

		Message rejeitada = rabbitTemplate.receive(propriedades.filaDeadLetter(), ESPERA.toMillis());
		assertThat(rejeitada).isNotNull();
		assertThat(rejeitada.getMessageProperties().getMessageId()).isEqualTo(messageId.toString());
		assertThat(manipulador.chamadas(messageId)).isZero();
		assertThat(eventoProcessadoRepository.foiProcessado(messageId)).isFalse();
	}

	private void publicarComoBilling(String routingKey, String corpo) {
		rabbitTemplate.send(propriedades.exchangeBilling(), routingKey, mensagem(corpo));
	}

	private void publicarComoExecucao(String routingKey, String corpo) {
		rabbitTemplate.send(propriedades.exchangeExecucao(), routingKey, mensagem(corpo));
	}

	private static Message mensagem(String corpo) {
		return MessageBuilder.withBody(corpo.getBytes(StandardCharsets.UTF_8))
				.setContentType(MessageProperties.CONTENT_TYPE_JSON)
				.setMessageId(extrairMessageId(corpo))
				.build();
	}

	private static String extrairMessageId(String corpo) {
		int inicio = corpo.indexOf("\"messageId\":\"");
		return inicio < 0 ? null : corpo.substring(inicio + 13, inicio + 13 + 36);
	}

	private static String envelope(UUID messageId, String tipo, String comportamento, String correlationId) {
		return corpo(messageId.toString(), UUID.randomUUID(), tipo, comportamento, messageId.toString(), correlationId);
	}

	/** Envelope do contrato; {@code messageId} e {@code sagaId} nulos são omitidos. {@code marca} identifica a mensagem no teste. */
	private static String corpo(String messageId, UUID sagaId, String tipo, String comportamento, String marca,
			String correlationId) {
		String campoMessageId = messageId == null ? "" : "\"messageId\":\"%s\",".formatted(messageId);
		String campoSagaId = sagaId == null ? "" : "\"sagaId\":\"%s\",".formatted(sagaId);
		return """
				{%s%s"tipo":"%s","ordemServicoId":1,"correlationId":"%s",
				 "ocorridoEm":"2026-11-06T10:00:00Z","versao":1,"dados":{"comportamento":"%s","marca":"%s"}}
				""".formatted(campoMessageId, campoSagaId, tipo, correlationId, comportamento, marca);
	}

	/** Manipulador de teste: conta chamadas e simula sucesso, violação de regra ou falha transitória. */
	static class ManipuladorDeTeste implements ManipuladorEvento {

		private final Map<String, AtomicInteger> chamadas = new ConcurrentHashMap<>();
		private final Map<String, String> correlationIds = new ConcurrentHashMap<>();

		@Override
		public String tipo() {
			return "EventoDeTeste";
		}

		@Override
		public void manipular(EnvelopeMensagem envelope) {
			String marca = envelope.dados().get("marca").asString();
			int tentativa = chamadas.computeIfAbsent(marca, id -> new AtomicInteger()).incrementAndGet();
			correlationIds.put(marca, MDC.get(CorrelationIdFilter.CHAVE_DE_LOG));

			String comportamento = envelope.dados().get("comportamento").asString();
			if ("regra".equals(comportamento)) {
				throw new RegraNegocioException("EventoDeTeste não é aceito no status atual");
			}
			if (comportamento.startsWith("falhar:") && tentativa <= Integer.parseInt(comportamento.substring(7))) {
				throw new IllegalStateException("falha transitória simulada, tentativa " + tentativa);
			}
		}

		int chamadas(UUID messageId) {
			return chamadas(messageId.toString());
		}

		int chamadas(String marca) {
			AtomicInteger total = chamadas.get(marca);
			return total == null ? 0 : total.get();
		}

		String correlationId(UUID messageId) {
			return correlationIds.get(messageId.toString());
		}
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class Configuracao {

		@Bean
		ManipuladorDeTeste manipuladorDeTeste() {
			return new ManipuladorDeTeste();
		}
	}
}
