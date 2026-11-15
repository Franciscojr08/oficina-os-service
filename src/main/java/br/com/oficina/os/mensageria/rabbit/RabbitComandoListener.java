package br.com.oficina.os.mensageria.rabbit;

import br.com.oficina.os.mensageria.porta.ComandoListener;
import br.com.oficina.os.mensageria.porta.MensagemRecebida;
import br.com.oficina.os.mensageria.porta.MensagemRejeitadaException;
import br.com.oficina.os.shared.observabilidade.CorrelationIdFilter;
import br.com.oficina.os.shared.observabilidade.Telemetria;
import com.newrelic.api.agent.Trace;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Adaptador do RabbitMQ para a {@link ComandoListener}: escuta a fila de entrada, converte a mensagem
 * AMQP e entrega ao domínio.
 *
 * <ul>
 *   <li>{@link MensagemRejeitadaException} vira {@link AmqpRejectAndDontRequeueException}: a
 *   mensagem vai direto para a DLQ, sem nova tentativa.</li>
 *   <li>Qualquer outra exceção sobe como está, e o container tenta de novo
 *   ({@code spring.rabbitmq.listener.simple.retry}) antes de mandar para a DLQ.</li>
 * </ul>
 *
 * <p>O {@code @Trace} fica aqui, no ponto de entrada, para a mensagem ser uma transação do New Relic
 * ligada ao trace de quem a enviou.</p>
 */
@Component
@RequiredArgsConstructor
public class RabbitComandoListener {

	private final ComandoListener comandoListener;

	@Trace(dispatcher = true)
	@RabbitListener(queues = "${oficina.mensageria.fila-entrada}")
	public void receber(Message mensagem) {
		try {
			comandoListener.receber(converter(mensagem));
		} catch (MensagemRejeitadaException e) {
			throw new AmqpRejectAndDontRequeueException(e.getMessage(), e);
		}
	}

	static MensagemRecebida converter(Message mensagem) {
		MessageProperties propriedades = mensagem.getMessageProperties();
		Object correlationId = propriedades.getHeader(CorrelationIdFilter.CABECALHO);
		return new MensagemRecebida(
				new String(mensagem.getBody(), StandardCharsets.UTF_8),
				propriedades.getMessageId(),
				propriedades.getType(),
				correlationId != null ? correlationId.toString() : null,
				cabecalhosDeRastreamento(propriedades));
	}

	private static Map<String, String> cabecalhosDeRastreamento(MessageProperties propriedades) {
		Map<String, String> cabecalhos = new HashMap<>();
		for (String nome : Telemetria.CABECALHOS_RASTREAMENTO) {
			Object valor = propriedades.getHeader(nome);
			if (valor != null) {
				cabecalhos.put(nome, valor.toString());
			}
		}
		return cabecalhos;
	}
}
