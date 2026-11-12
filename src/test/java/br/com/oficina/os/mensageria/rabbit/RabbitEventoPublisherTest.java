package br.com.oficina.os.mensageria.rabbit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.oficina.os.mensageria.porta.FalhaPublicacaoException;
import br.com.oficina.os.mensageria.porta.MensagemSaida;
import br.com.oficina.os.shared.observabilidade.CorrelationIdFilter;
import com.rabbitmq.client.ShutdownSignalException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RabbitEventoPublisherTest {

	@Mock
	private RabbitTemplate rabbitTemplate;

	@Mock
	private RabbitOperations operacoes;

	private RabbitEventoPublisher publisher;

	@BeforeEach
	void setUp() {
		publisher = new RabbitEventoPublisher(rabbitTemplate);
		when(rabbitTemplate.invoke(any())).thenAnswer(invocacao ->
				invocacao.<RabbitOperations.OperationsCallback<?>>getArgument(0).doInRabbit(operacoes));
	}

	@Test
	void devePublicarNaExchangeComRoutingKeyEEsperarAConfirmacao() {
		MensagemSaida saida = saida(Map.of());

		publisher.publicar(saida);

		ArgumentCaptor<Message> mensagem = ArgumentCaptor.forClass(Message.class);
		verify(operacoes).send(eq("os.eventos"), eq("SolicitarOrcamento"), mensagem.capture());
		verify(operacoes).waitForConfirmsOrDie(anyLong());

		var propriedades = mensagem.getValue().getMessageProperties();
		assertThat(propriedades.getMessageId()).isEqualTo(saida.messageId().toString());
		assertThat(propriedades.getType()).isEqualTo("SolicitarOrcamento");
		assertThat(propriedades.getCorrelationId()).isEqualTo("corr-1");
		assertThat(propriedades.<String>getHeader(CorrelationIdFilter.CABECALHO)).isEqualTo("corr-1");
		assertThat(propriedades.<Long>getHeader(RabbitEventoPublisher.CABECALHO_ORDEM_SERVICO)).isEqualTo(10L);
		assertThat(propriedades.getContentType()).isEqualTo("application/json");
		assertThat(propriedades.getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
		assertThat(propriedades.getAppId()).isEqualTo("oficina-os-service");
		assertThat(new String(mensagem.getValue().getBody(), StandardCharsets.UTF_8)).isEqualTo("{\"tipo\":\"x\"}");
	}

	@Test
	void deveLevarOContextoDoTraceNosCabecalhos() {
		publisher.publicar(saida(Map.of("traceparent", "00-abc-def-01", "newrelic", "eyJ2Ijp9")));

		ArgumentCaptor<Message> mensagem = ArgumentCaptor.forClass(Message.class);
		verify(operacoes).send(eq("os.eventos"), eq("SolicitarOrcamento"), mensagem.capture());
		assertThat(mensagem.getValue().getMessageProperties().<String>getHeader("traceparent")).isEqualTo("00-abc-def-01");
		assertThat(mensagem.getValue().getMessageProperties().<String>getHeader("newrelic")).isEqualTo("eyJ2Ijp9");
	}

	@Test
	void falhaDoClienteAmqpViraFalhaDePublicacao() {
		AmqpConnectException erro = new AmqpConnectException(new RuntimeException("broker fora"));
		doThrow(erro).when(operacoes).waitForConfirmsOrDie(anyLong());

		assertThatThrownBy(() -> publisher.publicar(saida(Map.of())))
				.isInstanceOf(FalhaPublicacaoException.class)
				.hasMessageContaining("broker fora")
				.hasCause(erro);
	}

	@Test
	void fechamentoDoCanalPeloBrokerViraFalhaDePublicacao() {
		ShutdownSignalException erro = new ShutdownSignalException(true, false, null, null);
		doThrow(erro).when(operacoes).waitForConfirmsOrDie(anyLong());

		assertThatThrownBy(() -> publisher.publicar(saida(Map.of())))
				.isInstanceOf(FalhaPublicacaoException.class)
				.hasCause(erro);
	}

	private static MensagemSaida saida(Map<String, String> rastreamento) {
		return new MensagemSaida(UUID.randomUUID(), "SolicitarOrcamento", "os.eventos", "SolicitarOrcamento", 10L,
				"corr-1", "{\"tipo\":\"x\"}", LocalDateTime.now(), rastreamento);
	}
}
