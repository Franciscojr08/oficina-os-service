package br.com.oficina.os.ordemservico.application;

import static br.com.oficina.os.shared.exception.ExceptionMessage.CANNOT_CANCEL_PAID_SERVICE_ORDER;
import static br.com.oficina.os.shared.exception.ExceptionMessage.INVALID_ORDER_STATUS_FOR_ACTION;
import static br.com.oficina.os.shared.exception.ExceptionMessage.QUOTE_IN_PROGRESS;

import br.com.oficina.os.mensageria.evento.EventoSaida;
import br.com.oficina.os.mensageria.outbox.OutboxService;
import br.com.oficina.os.ordemservico.application.evento.OrdemServicoStatusAlteradoDados;
import br.com.oficina.os.ordemservico.application.gateway.OrdemServicoGateway;
import br.com.oficina.os.ordemservico.domain.OrdemServico;
import br.com.oficina.os.saga.domain.SagaOrdemServicoRepository;
import br.com.oficina.os.saga.domain.SituacaoSaga;
import br.com.oficina.os.shared.exception.RegraNegocioException;
import br.com.oficina.os.shared.observabilidade.OrdemServicoObservabilidade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Máquina de estados da OS. Toda mudança de status passa por aqui: valida o status de origem,
 * carimba a data da etapa, grava a OS, registra o histórico (com a origem da mudança), grava o
 * evento {@code OrdemServicoStatusAlterado} no outbox e emite os eventos de observabilidade.
 */
@Service
@RequiredArgsConstructor
public class OrdemServicoStatusService {

	/** Origem padrão do histórico quando a mudança vem de uma chamada da API REST. */
	public static final String ORIGEM_API = "API";

	private final OrdemServicoGateway ordemServicoRepository;
	private final HistoricoOrdemServicoService historicoOrdemServicoService;
	private final OrdemServicoObservabilidade ordemServicoObservabilidade;
	private final OutboxService outboxService;
	private final SagaOrdemServicoRepository sagaRepository;

	public void definirStatusInicial(OrdemServico ordemServico) {
		// Não registra o evento de observabilidade aqui: a OS ainda não tem id/código nesse ponto
		// (são atribuídos no INSERT — id por sequence, código por trigger). Quem registra a criação
		// é o chamador, depois do saveAndFlush + refresh (ver OrdemServicoService.criar).
		ordemServico.setStatus(StatusOrdemServico.RECEBIDA);
	}

	/**
	 * Registra a abertura, depois que a OS já tem id (sequence) e código (trigger), atribuídos no
	 * INSERT: histórico, evento de status e observabilidade.
	 */
	public void registrarAbertura(OrdemServico ordemServico) {
		historicoOrdemServicoService.registrar(ordemServico, StatusOrdemServico.RECEBIDA, ORIGEM_API);
		publicarStatusAlterado(ordemServico, null, StatusOrdemServico.RECEBIDA, ORIGEM_API);
		ordemServicoObservabilidade.registrarMudancaStatus(ordemServico, null, StatusOrdemServico.RECEBIDA);
	}

	public void validarEmDiagnostico(OrdemServico ordemServico, String mensagem) {
		if (!ordemServico.getStatus().estaEmDiagnostico()) {
			throw new RegraNegocioException(mensagem);
		}
		validarSemOrcamentoEmAndamento(ordemServico);
	}

	/**
	 * Os dados da OS (cliente, veículo, descrições) só podem mudar antes do pedido de orçamento:
	 * depois disso o Billing já recebeu a OS para orçar.
	 */
	public void validarPermiteAlteracao(OrdemServico ordemServico) {
		StatusOrdemServico status = ordemServico.getStatus();
		if (status != StatusOrdemServico.RECEBIDA && status != StatusOrdemServico.EM_DIAGNOSTICO) {
			throw new RegraNegocioException(INVALID_ORDER_STATUS_FOR_ACTION
					.formatted("alterar a ordem de serviço", status.getDescricao()));
		}
		validarSemOrcamentoEmAndamento(ordemServico);
	}

	/**
	 * Enquanto o Billing gera o orçamento a OS continua em EM_DIAGNOSTICO (a espera é uma etapa da
	 * Saga, não um status), mas o pedido já foi enviado com itens e serviços: mudá-los agora
	 * deixaria o orçamento diferente da OS.
	 */
	private void validarSemOrcamentoEmAndamento(OrdemServico ordemServico) {
		boolean emAndamento = ordemServico.getId() != null && sagaRepository
				.findByOrdemServicoId(ordemServico.getId())
				.filter(saga -> saga.getSituacao() == SituacaoSaga.EM_ANDAMENTO)
				.isPresent();
		if (emAndamento) {
			throw new RegraNegocioException(QUOTE_IN_PROGRESS);
		}
	}

	public void iniciarDiagnostico(OrdemServico ordemServico) {
		transicionar(ordemServico, StatusOrdemServico.RECEBIDA, StatusOrdemServico.EM_DIAGNOSTICO,
				"iniciar diagnóstico", ORIGEM_API);
	}

	/**
	 * Pedido de orçamento enviado ao Billing. O status não muda (a OS segue em EM_DIAGNOSTICO até o
	 * {@code OrcamentoGerado}); só carimba a data do pedido e fecha a etapa de diagnóstico.
	 */
	public void solicitarOrcamento(OrdemServico ordemServico) {
		validarStatus(ordemServico, List.of(StatusOrdemServico.EM_DIAGNOSTICO), "solicitar orçamento");
		ordemServico.setDataSolicitacaoOrcamento(LocalDateTime.now());
		ordemServicoRepository.saveAndFlush(ordemServico);
		ordemServicoObservabilidade.registrarDuracaoEtapa(
				ordemServico, "DIAGNOSTICO", ordemServico.getDataCadastro(), ordemServico.getDataSolicitacaoOrcamento());
	}

	public void registrarOrcamentoGerado(OrdemServico ordemServico, Long orcamentoId, String origem) {
		validarStatus(ordemServico, List.of(StatusOrdemServico.EM_DIAGNOSTICO), "registrar o orçamento");
		ordemServico.setOrcamentoId(orcamentoId);
		atualizarStatus(ordemServico, StatusOrdemServico.AGUARDANDO_APROVACAO, origem);
	}

	/**
	 * Compensação: o Billing não respondeu ao pedido. A OS já está em EM_DIAGNOSTICO e pode pedir de
	 * novo; só fica no histórico o motivo do retorno.
	 */
	public void registrarOrcamentoNaoGerado(OrdemServico ordemServico, String origem) {
		validarStatus(ordemServico, List.of(StatusOrdemServico.EM_DIAGNOSTICO), "retornar para diagnóstico");
		historicoOrdemServicoService.registrar(ordemServico, StatusOrdemServico.EM_DIAGNOSTICO, origem);
	}

	/**
	 * O cliente aprovou o orçamento. O status só muda para APROVADA quando o Billing confirmar o
	 * pagamento; até lá a OS segue em AGUARDANDO_APROVACAO e a Saga em AGUARDANDO_PAGAMENTO.
	 */
	public void registrarAprovacao(OrdemServico ordemServico) {
		validarStatus(ordemServico, List.of(StatusOrdemServico.AGUARDANDO_APROVACAO), "aprovar a ordem de serviço");
		ordemServico.setDataAprovacao(LocalDateTime.now());
		ordemServicoRepository.saveAndFlush(ordemServico);
	}

	public void registrarPagamento(OrdemServico ordemServico, Long pagamentoId, String origem) {
		validarStatus(ordemServico, List.of(StatusOrdemServico.AGUARDANDO_APROVACAO), "registrar o pagamento");
		ordemServico.setPagamentoId(pagamentoId);
		atualizarStatus(ordemServico, StatusOrdemServico.APROVADA, origem);
	}

	public void aguardarItens(OrdemServico ordemServico, String origem) {
		transicionar(ordemServico, StatusOrdemServico.APROVADA, StatusOrdemServico.AGUARDANDO_ITENS,
				"aguardar itens", origem);
	}

	/** A execução começa direto da aprovação ou depois de esperar as peças. */
	public void iniciarExecucao(OrdemServico ordemServico, String origem) {
		validarStatus(ordemServico, List.of(StatusOrdemServico.APROVADA, StatusOrdemServico.AGUARDANDO_ITENS),
				"iniciar execução");
		atualizarStatus(ordemServico, StatusOrdemServico.EM_EXECUCAO, origem);
	}

	public void finalizar(OrdemServico ordemServico, String origem) {
		transicionar(ordemServico, StatusOrdemServico.EM_EXECUCAO, StatusOrdemServico.FINALIZADA,
				"finalizar a ordem de serviço", origem);
	}

	public void entregar(OrdemServico ordemServico) {
		transicionar(ordemServico, StatusOrdemServico.FINALIZADA, StatusOrdemServico.ENTREGUE, "entregar", ORIGEM_API);
	}

	/**
	 * Cancela a OS antes do início da execução. Com pagamento registrado o cancelamento passa pelo
	 * estorno (compensação da Saga, ver {@code OrquestradorSaga.cancelar}); aqui ele é recusado para
	 * nunca deixar uma OS cancelada com o pagamento retido.
	 */
	public void cancelar(OrdemServico ordemServico, String origem) {
		validarCancelavel(ordemServico);
		if (ordemServico.getPagamentoId() != null) {
			throw new RegraNegocioException(CANNOT_CANCEL_PAID_SERVICE_ORDER);
		}
		atualizarStatus(ordemServico, StatusOrdemServico.CANCELADA, origem);
	}

	/** Recusa o cancelamento se a execução já começou ou se a OS já está encerrada. */
	public void validarCancelavel(OrdemServico ordemServico) {
		validarStatus(ordemServico, StatusOrdemServico.statusCancelaveis(), "cancelar a ordem de serviço");
	}

	/**
	 * Fim da compensação: o Billing confirmou o estorno e a OS paga é cancelada. Vale também para a
	 * OS em EM_EXECUCAO, quando a Execução falha no meio do reparo.
	 */
	public void cancelarAposEstorno(OrdemServico ordemServico, String origem) {
		List<StatusOrdemServico> permitidos = new ArrayList<>(StatusOrdemServico.statusCancelaveis());
		permitidos.add(StatusOrdemServico.EM_EXECUCAO);
		validarStatus(ordemServico, permitidos, "cancelar a ordem de serviço");
		atualizarStatus(ordemServico, StatusOrdemServico.CANCELADA, origem);
	}

	private void transicionar(
			OrdemServico ordemServico,
			StatusOrdemServico statusAtualPermitido,
			StatusOrdemServico novoStatus,
			String acao,
			String origem
	) {
		validarStatus(ordemServico, List.of(statusAtualPermitido), acao);
		atualizarStatus(ordemServico, novoStatus, origem);
	}

	private void validarStatus(OrdemServico ordemServico, List<StatusOrdemServico> statusPermitidos, String acao) {
		if (!statusPermitidos.contains(ordemServico.getStatus())) {
			String motivo = INVALID_ORDER_STATUS_FOR_ACTION
					.formatted(acao, ordemServico.getStatus().getDescricao());

			ordemServicoObservabilidade.registrarTransicaoInvalida(ordemServico, acao, motivo);

			throw new RegraNegocioException(motivo);
		}
	}

	private void atualizarStatus(OrdemServico ordemServico, StatusOrdemServico status, String origem) {
		StatusOrdemServico statusAnterior = ordemServico.getStatus();
		ordemServico.setStatus(status);
		carimbarDataDaEtapa(ordemServico, status);

		ordemServicoRepository.saveAndFlush(ordemServico);
		historicoOrdemServicoService.registrar(ordemServico, status, origem);
		publicarStatusAlterado(ordemServico, statusAnterior, status, origem);

		ordemServicoObservabilidade.registrarMudancaStatus(ordemServico, statusAnterior, status);
		registrarDuracaoDeEtapa(ordemServico, status);
	}

	private void publicarStatusAlterado(
			OrdemServico ordemServico, StatusOrdemServico anterior, StatusOrdemServico atual, String origem) {
		// Evento informativo: sai também fora de uma Saga (a abertura da OS, por exemplo), então sem sagaId
		outboxService.registrar(EventoSaida.ORDEM_SERVICO_STATUS_ALTERADO, ordemServico.getId(),
				ordemServico.getCodigo(), null,
				new OrdemServicoStatusAlteradoDados(ordemServico.getId(), ordemServico.getCodigo(), anterior, atual,
						origem, LocalDateTime.now()));
	}

	/**
	 * Alimenta o dashboard de "tempo médio de execução por status", mapeando as três etapas
	 * nomeadas (Diagnóstico, Execução, Finalização) para as datas que a própria entidade mantém.
	 */
	private void registrarDuracaoDeEtapa(OrdemServico ordemServico, StatusOrdemServico status) {
		switch (status) {
			case FINALIZADA -> ordemServicoObservabilidade.registrarDuracaoEtapa(
					ordemServico, "EXECUCAO", ordemServico.getDataInicioExecucao(), ordemServico.getDataFimExecucao()
			);
			case ENTREGUE -> ordemServicoObservabilidade.registrarDuracaoEtapa(
					ordemServico, "FINALIZACAO", ordemServico.getDataFimExecucao(), ordemServico.getDataEntregue()
			);
			default -> { /* intencional: demais status não têm etapa de duração no dashboard */ }
		}
	}

	private void carimbarDataDaEtapa(OrdemServico ordemServico, StatusOrdemServico status) {
		LocalDateTime agora = LocalDateTime.now();
		switch (status) {
			case AGUARDANDO_APROVACAO -> ordemServico.setDataEnvioAprovacao(agora);
			case APROVADA -> ordemServico.setDataPagamento(agora);
			case EM_EXECUCAO -> ordemServico.setDataInicioExecucao(agora);
			case FINALIZADA -> ordemServico.setDataFimExecucao(agora);
			case ENTREGUE -> ordemServico.setDataEntregue(agora);
			case CANCELADA -> ordemServico.setDataCancelada(agora);
			default -> { /* demais status não têm data própria (a data de aprovação é carimbada na aprovação do cliente) */ }
		}
	}
}
