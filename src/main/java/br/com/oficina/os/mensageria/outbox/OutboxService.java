package br.com.oficina.os.mensageria.outbox;

import br.com.oficina.os.mensageria.config.MensageriaProperties;
import br.com.oficina.os.mensageria.evento.EnvelopeMensagem;
import br.com.oficina.os.mensageria.evento.EventoSaida;
import br.com.oficina.os.shared.observabilidade.CorrelationIdFilter;
import br.com.oficina.os.shared.observabilidade.Telemetria;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Registra comandos e eventos no outbox. Exige transação aberta ({@code MANDATORY}): o evento só
 * existe se a mudança na OS for confirmada, e a mudança só é confirmada junto com o evento.
 */
@Service
@RequiredArgsConstructor
public class OutboxService {

	private final OutboxEventoRepository repository;
	private final JsonMapper jsonMapper;
	private final MensageriaProperties propriedades;
	private final Telemetria telemetria;

	/**
	 * Grava o comando ou evento no outbox.
	 *
	 * @param sagaId Saga da mensagem; nulo só para evento informativo fora de uma Saga
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public UUID registrar(
			EventoSaida evento, Long ordemServicoId, String codigoOrdemServico, UUID sagaId, Object dados) {
		UUID messageId = UUID.randomUUID();
		String correlationId = correlationIdAtual();

		EnvelopeMensagem envelope = new EnvelopeMensagem(
				messageId,
				evento.getTipo(),
				sagaId,
				ordemServicoId,
				codigoOrdemServico,
				correlationId,
				Instant.now(),
				EnvelopeMensagem.VERSAO_ATUAL,
				jsonMapper.valueToTree(dados)
		);

		OutboxEvento registro = new OutboxEvento();
		registro.setMessageId(messageId);
		registro.setTipo(evento.getTipo());
		registro.setExchange(propriedades.exchangeOs());
		registro.setRoutingKey(evento.getTipo());
		registro.setOrdemServicoId(ordemServicoId);
		registro.setSagaId(sagaId);
		registro.setCorrelationId(correlationId);
		registro.setPayload(jsonMapper.writeValueAsString(envelope));
		// O publicador roda em outra transação: o contexto do trace atual segue guardado com o evento
		Map<String, String> rastreamento = telemetria.capturarRastreamento();
		if (!rastreamento.isEmpty()) {
			registro.setRastreamento(jsonMapper.writeValueAsString(rastreamento));
		}
		repository.save(registro);

		return messageId;
	}

	/**
	 * Publica de novo um evento já registrado, com o mesmo {@code messageId}: se o destino já o
	 * tinha recebido, descarta a repetição. Devolve {@code false} se o evento não está mais no
	 * outbox (removido pela retenção).
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public boolean reenviar(UUID messageId) {
		return repository.marcarParaReenvio(messageId) == 1;
	}

	/**
	 * O mesmo id da requisição HTTP (ou da mensagem consumida) que provocou o evento; fora desses
	 * contextos, um id novo.
	 */
	private static String correlationIdAtual() {
		String correlationId = MDC.get(CorrelationIdFilter.CHAVE_DE_LOG);
		return correlationId != null ? correlationId : UUID.randomUUID().toString();
	}
}
