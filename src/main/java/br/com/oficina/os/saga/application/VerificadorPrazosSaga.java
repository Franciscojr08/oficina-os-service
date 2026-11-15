package br.com.oficina.os.saga.application;

import br.com.oficina.os.mensageria.outbox.OutboxService;
import br.com.oficina.os.ordemservico.application.OrdemServicoStatusService;
import br.com.oficina.os.ordemservico.application.gateway.OrdemServicoGateway;
import br.com.oficina.os.ordemservico.domain.OrdemServico;
import br.com.oficina.os.saga.config.SagaProperties;
import br.com.oficina.os.saga.domain.EtapaSaga;
import br.com.oficina.os.saga.domain.SagaOrdemServico;
import br.com.oficina.os.saga.domain.SagaOrdemServicoRepository;
import br.com.oficina.os.saga.domain.SituacaoSaga;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Procura Sagas paradas além do prazo. Enquanto houver tentativas, reenvia o mesmo comando (mesmo
 * {@code messageId}, então o destino descarta a repetição se já o tinha recebido); esgotadas, dispara
 * a compensação da etapa. Cada Saga é tratada na própria transação: uma falha não trava as outras.
 */
@Component
@RequiredArgsConstructor
public class VerificadorPrazosSaga {

	/** Origem gravada no histórico quando a OS volta ao diagnóstico por falta de resposta do Billing. */
	public static final String ORIGEM_PRAZO_ORCAMENTO = "PrazoOrcamentoEsgotado";

	private static final Logger LOG = LoggerFactory.getLogger(VerificadorPrazosSaga.class);

	private final SagaOrdemServicoRepository sagaRepository;
	private final OrdemServicoGateway ordemServicoGateway;
	private final OrdemServicoStatusService statusService;
	private final OutboxService outboxService;
	private final TransactionTemplate transactionTemplate;
	private final SagaProperties propriedades;
	private final SagaObservabilidade observabilidade;

	/** Trata as Sagas vencidas e devolve quantas foram tratadas. */
	public int verificar() {
		LocalDateTime agora = LocalDateTime.now();
		int tratadas = 0;
		for (Long id : sagaRepository.idsComPrazoVencido(agora, propriedades.verificacao().lote())) {
			try {
				Boolean tratada = transactionTemplate.execute(status -> sagaRepository.travarSeVencida(id, agora)
						.map(saga -> {
							tratar(saga);
							return true;
						})
						.orElse(false));
				if (Boolean.TRUE.equals(tratada)) {
					tratadas++;
				}
			} catch (RuntimeException e) {
				LOG.error("saga_falha_verificacao_prazo sagaId={} erro={}", id, e.getMessage(), e);
			}
		}
		return tratadas;
	}

	private void tratar(SagaOrdemServico saga) {
		if (saga.getTentativas() + 1 < propriedades.maxTentativas()) {
			reenviar(saga);
		} else {
			esgotar(saga);
		}
	}

	private void reenviar(SagaOrdemServico saga) {
		if (saga.getComandoMessageId() == null || !outboxService.reenviar(saga.getComandoMessageId())) {
			LOG.warn("saga_comando_indisponivel ordemServicoId={} etapa={} comando={}",
					saga.getOrdemServicoId(), saga.getEtapa(), saga.getComandoMessageId());
		}
		saga.registrarReenvio(prazoDa(saga.getEtapa()));
		LOG.info("saga_comando_reenviado ordemServicoId={} etapa={} tentativa={}",
				saga.getOrdemServicoId(), saga.getEtapa(), saga.getTentativas() + 1);
		observabilidade.registrarReenvio(saga);
	}

	private void esgotar(SagaOrdemServico saga) {
		if (saga.getEtapa() == EtapaSaga.AGUARDANDO_ORCAMENTO) {
			OrdemServico ordemServico = ordemServicoGateway.findById(saga.getOrdemServicoId()).orElseThrow();
			statusService.registrarOrcamentoNaoGerado(ordemServico, ORIGEM_PRAZO_ORCAMENTO);
			saga.encerrar(SituacaoSaga.COMPENSADA, "Billing não respondeu ao pedido de orçamento");
			observabilidade.registrarTransicao(saga, ORIGEM_PRAZO_ORCAMENTO);
			LOG.warn("saga_prazo_esgotado ordemServicoId={} etapa={} acao=retorno_ao_diagnostico",
					saga.getOrdemServicoId(), saga.getEtapa());
			return;
		}
		// Estorno sem confirmação: não há como desfazer sozinho, alguém precisa olhar o pagamento.
		saga.encerrar(SituacaoSaga.FALHA_COMPENSACAO, "Billing não confirmou o estorno após "
				+ propriedades.maxTentativas() + " tentativas");
		observabilidade.registrarTransicao(saga, "PrazoEstornoEsgotado");
		LOG.error("saga_falha_compensacao ordemServicoId={} etapa={} acao=intervencao_manual",
				saga.getOrdemServicoId(), saga.getEtapa());
	}

	private Duration prazoDa(EtapaSaga etapa) {
		return etapa == EtapaSaga.AGUARDANDO_ESTORNO ? propriedades.prazoEstorno() : propriedades.prazoOrcamento();
	}
}
