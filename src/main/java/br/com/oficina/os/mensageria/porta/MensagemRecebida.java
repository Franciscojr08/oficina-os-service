package br.com.oficina.os.mensageria.porta;

import java.util.Map;

/**
 * Mensagem entregue pelo broker ao OS Service, sem nada específico de broker.
 *
 * @param corpo envelope em JSON, como chegou
 * @param messageId id da mensagem nas propriedades do broker (pode faltar em mensagem malformada)
 * @param tipo tipo da mensagem nas propriedades do broker (pode faltar em mensagem malformada)
 * @param correlationId id de correlação do cabeçalho, ou nulo se a mensagem não trouxe
 * @param rastreamento cabeçalhos do trace de quem enviou (traceparent, newrelic etc.)
 */
public record MensagemRecebida(
		String corpo,
		String messageId,
		String tipo,
		String correlationId,
		Map<String, String> rastreamento
) {

	public MensagemRecebida {
		rastreamento = rastreamento == null ? Map.of() : Map.copyOf(rastreamento);
	}
}
