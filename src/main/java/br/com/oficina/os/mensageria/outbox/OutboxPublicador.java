package br.com.oficina.os.mensageria.outbox;

import br.com.oficina.os.mensageria.config.MensageriaProperties;
import br.com.oficina.os.mensageria.porta.EventoPublisherPort;
import br.com.oficina.os.mensageria.porta.FalhaPublicacaoException;
import br.com.oficina.os.mensageria.porta.MensagemSaida;
import br.com.oficina.os.shared.observabilidade.Telemetria;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Publica os eventos pendentes do outbox. Cada evento só é marcado como publicado depois da
 * confirmação do broker (publisher confirm); se o envio falhar, o evento continua pendente, a
 * tentativa é registrada e a rodada para ali, para não inverter a ordem dos eventos.
 *
 * <p>A entrega é "pelo menos uma vez": se o processo cair entre a confirmação e o commit, o evento
 * é publicado de novo na próxima rodada, e quem consome descarta a repetição pelo {@code messageId}.</p>
 */
@Component
@RequiredArgsConstructor
public class OutboxPublicador {

	private static final int TAMANHO_MAXIMO_ERRO = 500;
	private static final Logger LOG = LoggerFactory.getLogger(OutboxPublicador.class);
	private static final TypeReference<Map<String, String>> MAPA = new TypeReference<>() { };

	/** Eventos ainda não publicados: se só cresce, o broker está fora ou o publicador parou. */
	public static final String METRICA_PENDENTES = "Custom/Mensageria/OutboxPendentes";
	public static final String EVENTO_FALHA = "OutboxFalhaPublicacao";

	private final OutboxEventoRepository repository;
	private final EventoPublisherPort publisher;
	private final TransactionTemplate transactionTemplate;
	private final MensageriaProperties propriedades;
	private final JsonMapper jsonMapper;
	private final Telemetria telemetria;

	/** Publica uma rodada de pendentes e devolve quantos foram confirmados pelo broker. */
	public int publicarPendentes() {
		Integer publicados = transactionTemplate.execute(status -> {
			int confirmados = 0;
			for (OutboxEvento evento : repository.travarPendentes(propriedades.outbox().lote())) {
				try {
					enviar(evento);
				} catch (FalhaPublicacaoException e) {
					registrarFalha(evento, e);
					break;
				}
				evento.setPublicadoEm(LocalDateTime.now());
				evento.setUltimoErro(null);
				confirmados++;
				LOG.info("outbox_publicado tipo={} messageId={} ordemServicoId={}",
						evento.getTipo(), evento.getMessageId(), evento.getOrdemServicoId());
			}
			return confirmados;
		});
		telemetria.registrarMetrica(METRICA_PENDENTES, repository.countByPublicadoEmIsNull());
		return publicados == null ? 0 : publicados;
	}

	private void enviar(OutboxEvento evento) {
		publisher.publicar(new MensagemSaida(
				evento.getMessageId(),
				evento.getTipo(),
				evento.getExchange(),
				evento.getRoutingKey(),
				evento.getOrdemServicoId(),
				evento.getCorrelationId(),
				evento.getPayload(),
				evento.getCriadoEm(),
				// Contexto do trace de quem gerou o evento: quem consome continua o mesmo trace
				rastreamento(evento)));
	}

	private Map<String, String> rastreamento(OutboxEvento evento) {
		if (evento.getRastreamento() == null) {
			return Map.of();
		}
		try {
			return jsonMapper.readValue(evento.getRastreamento(), MAPA);
		} catch (RuntimeException e) {
			// Trace perdido não impede a entrega do evento
			LOG.warn("outbox_rastreamento_invalido messageId={}", evento.getMessageId());
			return Map.of();
		}
	}

	private void registrarFalha(OutboxEvento evento, RuntimeException erro) {
		evento.setTentativas(evento.getTentativas() + 1);
		String mensagem = String.valueOf(erro.getMessage());
		if (mensagem.length() > TAMANHO_MAXIMO_ERRO) {
			mensagem = mensagem.substring(0, TAMANHO_MAXIMO_ERRO);
		}
		evento.setUltimoErro(mensagem);
		LOG.warn("outbox_falha_publicacao tipo={} messageId={} tentativas={} erro={}",
				evento.getTipo(), evento.getMessageId(), evento.getTentativas(), mensagem);
		telemetria.registrarEvento(EVENTO_FALHA, Map.of(
				"tipo", evento.getTipo(),
				"messageId", evento.getMessageId().toString(),
				"tentativas", evento.getTentativas(),
				"erro", mensagem));
	}
}
