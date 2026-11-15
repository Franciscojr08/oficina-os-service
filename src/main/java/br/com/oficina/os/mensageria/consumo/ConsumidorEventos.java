package br.com.oficina.os.mensageria.consumo;

import br.com.oficina.os.mensageria.evento.EnvelopeMensagem;
import br.com.oficina.os.mensageria.porta.ComandoListener;
import br.com.oficina.os.mensageria.porta.MensagemRecebida;
import br.com.oficina.os.mensageria.porta.MensagemRejeitadaException;
import br.com.oficina.os.shared.exception.RegraNegocioException;
import br.com.oficina.os.shared.observabilidade.CorrelationIdFilter;
import br.com.oficina.os.shared.observabilidade.Telemetria;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Trata as mensagens que chegam na entrada do OS Service (eventos do Billing e da Execução). É o
 * núcleo da porta {@link ComandoListener}: não conhece o broker, só a {@link MensagemRecebida}.
 *
 * <ul>
 *   <li>Mensagem inválida, ou que contradiz o estado da OS, lança {@link MensagemRejeitadaException}:
 *   o adaptador a manda direto para a DLQ, porque tentar de novo não muda o resultado.</li>
 *   <li>Qualquer outra falha (banco fora, por exemplo) sobe como está, e o adaptador tenta de novo
 *   antes de mandar para a DLQ.</li>
 * </ul>
 *
 * <p>O correlationId da mensagem vai para o MDC, então os logs e os eventos publicados em
 * consequência dela continuam correlacionados com a requisição original. No New Relic, cada mensagem
 * é uma transação ({@code Mensagem/<tipo>}) ligada ao trace de quem a enviou, pelos cabeçalhos
 * {@code traceparent}/{@code newrelic}; os comandos publicados em seguida levam o mesmo trace.</p>
 */
@Component
@RequiredArgsConstructor
public class ConsumidorEventos implements ComandoListener {

	public static final String EVENTO_REJEITADA = "MensagemRejeitada";

	private static final Logger LOG = LoggerFactory.getLogger(ConsumidorEventos.class);

	private final ProcessadorEventos processador;
	private final JsonMapper jsonMapper;
	private final Telemetria telemetria;

	@Override
	public void receber(MensagemRecebida mensagem) {
		telemetria.continuarRastreamento(mensagem.rastreamento());
		telemetria.nomearTransacao("Mensagem", String.valueOf(mensagem.tipo()));
		MDC.put(CorrelationIdFilter.CHAVE_DE_LOG, correlationIdInicial(mensagem));
		EnvelopeMensagem envelope = null;
		try {
			envelope = ler(mensagem);
			if (envelope.correlationId() != null) {
				MDC.put(CorrelationIdFilter.CHAVE_DE_LOG, envelope.correlationId());
			}
			processador.processar(envelope);
		} catch (MensagemInvalidaException | RegraNegocioException e) {
			String messageId = String.valueOf(envelope != null ? envelope.messageId() : mensagem.messageId());
			String tipo = String.valueOf(envelope != null ? envelope.tipo() : mensagem.tipo());
			LOG.warn("mensagem_rejeitada destino=dlq messageId={} tipo={} motivo={}", messageId, tipo, e.getMessage());
			Map<String, Object> atributos = new HashMap<>();
			atributos.put("messageId", messageId);
			atributos.put("tipo", tipo);
			if (envelope != null && envelope.sagaId() != null) {
				atributos.put("sagaId", envelope.sagaId().toString());
			}
			atributos.put("motivo", String.valueOf(e.getMessage()));
			if (envelope != null && envelope.ordemServicoId() != null) {
				atributos.put("ordemServicoId", envelope.ordemServicoId());
			}
			telemetria.registrarEvento(EVENTO_REJEITADA, atributos);
			throw new MensagemRejeitadaException(e.getMessage(), e);
		} finally {
			MDC.remove(CorrelationIdFilter.CHAVE_DE_LOG);
		}
	}

	private EnvelopeMensagem ler(MensagemRecebida mensagem) {
		EnvelopeMensagem envelope;
		try {
			envelope = jsonMapper.readValue(mensagem.corpo(), EnvelopeMensagem.class);
		} catch (JacksonException e) {
			throw new MensagemInvalidaException("Corpo da mensagem não é um envelope JSON válido", e);
		}
		if (envelope == null || envelope.tipo() == null || envelope.tipo().isBlank()) {
			throw new MensagemInvalidaException("Envelope sem tipo");
		}
		// Todo comando e evento da Saga carrega o sagaId; sem ele não há como saber a que ciclo pertence
		if (envelope.sagaId() == null) {
			throw new MensagemInvalidaException("Envelope sem sagaId");
		}
		return envelope;
	}

	private static String correlationIdInicial(MensagemRecebida mensagem) {
		return mensagem.correlationId() != null ? mensagem.correlationId() : UUID.randomUUID().toString();
	}
}
