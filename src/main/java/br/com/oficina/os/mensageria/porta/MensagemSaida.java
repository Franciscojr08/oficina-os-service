package br.com.oficina.os.mensageria.porta;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Mensagem pronta para sair do OS Service, sem nada específico de broker.
 *
 * @param messageId id único da mensagem (chave de deduplicação de quem consome)
 * @param tipo nome do comando ou evento
 * @param destino exchange (RabbitMQ) ou tópico (SNS) que recebe a mensagem
 * @param chaveRoteamento routing key (RabbitMQ) ou atributo de filtro (SNS)
 * @param ordemServicoId OS a que a mensagem se refere
 * @param correlationId id que acompanha a requisição original
 * @param corpo envelope já serializado em JSON
 * @param criadoEm momento em que o fato aconteceu
 * @param rastreamento contexto do trace de quem gerou a mensagem (traceparent, newrelic etc.)
 */
public record MensagemSaida(
		UUID messageId,
		String tipo,
		String destino,
		String chaveRoteamento,
		Long ordemServicoId,
		String correlationId,
		String corpo,
		LocalDateTime criadoEm,
		Map<String, String> rastreamento
) {

	public MensagemSaida {
		rastreamento = rastreamento == null ? Map.of() : Map.copyOf(rastreamento);
	}
}
