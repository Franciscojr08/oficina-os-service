package br.com.oficina.os.saga.application;

import br.com.oficina.os.saga.domain.SagaOrdemServico;
import br.com.oficina.os.shared.observabilidade.Telemetria;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Eventos de negócio da Saga: cada transição (etapa, situação, gatilho), compensações, reenvios e
 * duração da Saga ao encerrar. Alimentam o dashboard do fluxo distribuído
 * (docs/observabilidade/dashboard-saga.json).
 */
@Component
@RequiredArgsConstructor
public class SagaObservabilidade {

	/** Toda transição da Saga. */
	public static final String EVENTO_TRANSICAO = "SagaOrdemServicoTransicao";
	/** Comando reenviado por falta de resposta no prazo. */
	public static final String EVENTO_REENVIO = "SagaComandoReenviado";

	private static final Logger LOG = LoggerFactory.getLogger(SagaObservabilidade.class);

	private final Telemetria telemetria;

	public void registrarTransicao(SagaOrdemServico saga, String gatilho) {
		LOG.info("saga_transicao sagaId={} ordemServicoId={} gatilho={} etapa={} situacao={}",
				saga.getSagaId(), saga.getOrdemServicoId(), gatilho, saga.getEtapa(), saga.getSituacao());

		Map<String, Object> atributos = atributos(saga);
		atributos.put("gatilho", gatilho);
		atributos.put("encerrada", !saga.getSituacao().ativa());
		if (saga.getMotivo() != null) {
			atributos.put("motivo", saga.getMotivo());
		}
		if (!saga.getSituacao().ativa() && saga.getIniciadaEm() != null && saga.getEncerradaEm() != null) {
			atributos.put("duracaoMs", Duration.between(saga.getIniciadaEm(), saga.getEncerradaEm()).toMillis());
		}
		telemetria.registrarEvento(EVENTO_TRANSICAO, atributos);
	}

	public void registrarReenvio(SagaOrdemServico saga) {
		Map<String, Object> atributos = atributos(saga);
		atributos.put("tentativa", saga.getTentativas() + 1);
		telemetria.registrarEvento(EVENTO_REENVIO, atributos);
	}

	private static Map<String, Object> atributos(SagaOrdemServico saga) {
		Map<String, Object> atributos = new HashMap<>();
		atributos.put("sagaId", String.valueOf(saga.getSagaId()));
		atributos.put("ordemServicoId", saga.getOrdemServicoId());
		atributos.put("etapa", saga.getEtapa().name());
		atributos.put("situacao", saga.getSituacao().name());
		atributos.put("tentativas", saga.getTentativas());
		return atributos;
	}
}
