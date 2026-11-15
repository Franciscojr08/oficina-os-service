package br.com.oficina.os.mensageria.rabbit;

import br.com.oficina.os.mensageria.porta.EventoPublisherPort;
import br.com.oficina.os.mensageria.porta.FalhaPublicacaoException;
import br.com.oficina.os.mensageria.porta.MensagemSaida;
import br.com.oficina.os.shared.observabilidade.CorrelationIdFilter;
import com.rabbitmq.client.ShutdownSignalException;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;

/**
 * Adaptador da {@link EventoPublisherPort} para o RabbitMQ: publica na exchange com a routing key e
 * espera a confirmação do broker (publisher confirm). As propriedades AMQP e os cabeçalhos são os do
 * contrato em {@code docs/mensageria.md}.
 */
@Component
@RequiredArgsConstructor
public class RabbitEventoPublisher implements EventoPublisherPort {

	/** Header com o id da OS, para filtrar e rastrear sem abrir o corpo da mensagem. */
	public static final String CABECALHO_ORDEM_SERVICO = "x-ordem-servico-id";

	private static final String APLICACAO = "oficina-os-service";
	private static final long TIMEOUT_CONFIRMACAO_MS = 5_000;

	private final RabbitTemplate rabbitTemplate;

	@Override
	public void publicar(MensagemSaida saida) {
		MessageBuilder construtor = MessageBuilder.withBody(saida.corpo().getBytes(StandardCharsets.UTF_8));
		// Contexto do trace de quem gerou o evento: quem consome continua o mesmo trace
		saida.rastreamento().forEach(construtor::setHeader);
		Message mensagem = construtor
				.setContentType(MessageProperties.CONTENT_TYPE_JSON)
				.setContentEncoding(StandardCharsets.UTF_8.name())
				.setMessageId(saida.messageId().toString())
				.setType(saida.tipo())
				.setCorrelationId(saida.correlationId())
				.setHeader(CorrelationIdFilter.CABECALHO, saida.correlationId())
				.setHeader(CABECALHO_ORDEM_SERVICO, saida.ordemServicoId())
				.setAppId(APLICACAO)
				.setTimestamp(Timestamp.valueOf(saida.criadoEm()))
				.setDeliveryMode(MessageDeliveryMode.PERSISTENT)
				.build();

		try {
			rabbitTemplate.invoke(operacoes -> {
				operacoes.send(saida.destino(), saida.chaveRoteamento(), mensagem);
				operacoes.waitForConfirmsOrDie(TIMEOUT_CONFIRMACAO_MS);
				return null;
			});
		} catch (AmqpException | ShutdownSignalException e) {
			// ShutdownSignalException chega crua do cliente do RabbitMQ quando o broker fecha o
			// canal (exchange inexistente, por exemplo) durante a espera da confirmação.
			throw new FalhaPublicacaoException(e.getMessage(), e);
		}
	}
}
