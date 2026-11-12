package br.com.oficina.os.saga.application;

import static br.com.oficina.os.shared.exception.ExceptionMessage.PAYMENT_IN_PROGRESS;

import br.com.oficina.os.mensageria.consumo.MensagemInvalidaException;
import br.com.oficina.os.mensageria.evento.EnvelopeMensagem;
import br.com.oficina.os.mensageria.evento.EventoSaida;
import br.com.oficina.os.mensageria.outbox.OutboxService;
import br.com.oficina.os.ordemservico.application.OrdemServicoStatusService;
import br.com.oficina.os.ordemservico.application.evento.SolicitarOrcamentoDados;
import br.com.oficina.os.ordemservico.application.gateway.OrdemServicoGateway;
import br.com.oficina.os.ordemservico.domain.OrdemServico;
import br.com.oficina.os.ordemservico.itens.application.gateway.ItemOrdemServicoGateway;
import br.com.oficina.os.ordemservico.itens.domain.ItemOrdemServico;
import br.com.oficina.os.ordemservico.servicos.application.gateway.ServicoOrdemServicoGateway;
import br.com.oficina.os.ordemservico.servicos.domain.ServicoOrdemServico;
import br.com.oficina.os.saga.config.SagaProperties;
import br.com.oficina.os.saga.domain.EtapaSaga;
import br.com.oficina.os.saga.domain.SagaOrdemServico;
import br.com.oficina.os.saga.domain.SagaOrdemServicoRepository;
import br.com.oficina.os.saga.domain.SituacaoSaga;
import br.com.oficina.os.saga.evento.CancelarExecucaoDados;
import br.com.oficina.os.saga.evento.CobrarPagamentoDados;
import br.com.oficina.os.saga.evento.EnfileirarExecucaoDados;
import br.com.oficina.os.saga.evento.EstornarPagamentoDados;
import br.com.oficina.os.shared.exception.RegraNegocioException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.UUID;

/**
 * Orquestrador da Saga da OS. O OS Service é dono do ciclo de vida da OS, então decide o próximo
 * passo e a compensação de cada falha; Billing e Execução só recebem comandos e respondem com
 * eventos.
 *
 * <pre>
 * SolicitarOrcamento → OrcamentoGerado (AGUARDANDO_APROVACAO)
 *   → [cliente aprova pela API] CobrarPagamento → PagamentoConfirmado (APROVADA)
 *   → EnfileirarExecucao → [ExecucaoAguardandoItens (AGUARDANDO_ITENS)] → ExecucaoIniciada (EM_EXECUCAO)
 *   → ServicoFinalizado (um por serviço) → ExecucaoFinalizada (FINALIZADA)
 *
 * Saídas de compensação:
 *   Sem resposta ao orçamento (prazo)  → OS segue em EM_DIAGNOSTICO e pode pedir de novo
 *   Cliente reprova (API)              → OS CANCELADA (nada a desfazer)
 *   PagamentoRecusado                  → OS CANCELADA (nada a desfazer)
 *   ExecucaoFalhou → EstornarPagamento → PagamentoEstornado → OS CANCELADA
 *   Cancelamento de OS paga → CancelarExecucao + EstornarPagamento → PagamentoEstornado
 * </pre>
 *
 * <p>O cliente e a oficina decidem pelo OS Service: aprovar publica {@code CobrarPagamento} e reprovar
 * cancela. O Billing não decide nada pelo cliente.</p>
 *
 * <p>Cada método roda na transação de quem chama (consumo da mensagem ou requisição HTTP): a mudança
 * de status, o estado da Saga e os comandos publicados no outbox são confirmados juntos. A Saga é
 * travada no banco, o que serializa eventos, cancelamento e verificação de prazo da mesma OS.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class OrquestradorSaga {

	static final String MOTIVO_REPROVACAO_API = "Orçamento reprovado pelo cliente";
	static final String MOTIVO_CANCELAMENTO_API = "Cancelamento solicitado pelo cliente ou pela oficina";

	private final SagaOrdemServicoRepository sagaRepository;
	private final OrdemServicoGateway ordemServicoGateway;
	private final ItemOrdemServicoGateway itemOrdemServicoGateway;
	private final ServicoOrdemServicoGateway servicoOrdemServicoGateway;
	private final OrdemServicoStatusService statusService;
	private final OutboxService outboxService;
	private final SagaProperties propriedades;
	private final SagaObservabilidade observabilidade;

	/** Início da Saga: o pedido de orçamento vai para o Billing e a OS segue em EM_DIAGNOSTICO até a resposta. */
	public void iniciar(OrdemServico ordemServico, List<ItemOrdemServico> itens, List<ServicoOrdemServico> servicos) {
		SagaOrdemServico saga = sagaRepository.travarPorOrdemServico(ordemServico.getId())
				.orElseGet(() -> SagaOrdemServico.para(ordemServico.getId()));
		if (saga.getSituacao() != null && saga.getSituacao().ativa()) {
			throw new RegraNegocioException("Já existe uma Saga em andamento para a ordem de serviço");
		}

		UUID sagaId = UUID.randomUUID();
		UUID comando = outboxService.registrar(EventoSaida.SOLICITAR_ORCAMENTO, ordemServico.getId(),
				ordemServico.getCodigo(), sagaId, SolicitarOrcamentoDados.de(ordemServico, itens, servicos));
		saga.iniciar(sagaId, comando, propriedades.prazoOrcamento());
		sagaRepository.save(saga);
		registrar(saga, "SolicitarOrcamento");
	}

	public void orcamentoGerado(EnvelopeMensagem evento) {
		Contexto contexto = carregar(evento, SituacaoSaga.EM_ANDAMENTO, EtapaSaga.AGUARDANDO_ORCAMENTO);
		long orcamentoId = numeroObrigatorio(evento, "orcamentoId");

		statusService.registrarOrcamentoGerado(contexto.ordemServico(), orcamentoId, evento.tipo());
		contexto.saga().avancar(EtapaSaga.AGUARDANDO_APROVACAO, null, null);
		registrar(contexto.saga(), evento.tipo());
	}

	/**
	 * O cliente aprovou o orçamento (pela API): o OS pede a cobrança ao Billing. A OS só passa para
	 * APROVADA quando o pagamento for confirmado.
	 */
	public void aprovar(OrdemServico ordemServico) {
		SagaOrdemServico saga = travarAguardandoAprovacao(ordemServico, "aprovar");

		statusService.registrarAprovacao(ordemServico);
		UUID comando = outboxService.registrar(EventoSaida.COBRAR_PAGAMENTO, ordemServico.getId(),
				ordemServico.getCodigo(), saga.getSagaId(), CobrarPagamentoDados.de(ordemServico));
		saga.avancar(EtapaSaga.AGUARDANDO_PAGAMENTO, comando, null);
		registrar(saga, "AprovacaoApi");
	}

	/** O cliente reprovou (pela API): nada foi cobrado nem reservado, a OS só é cancelada. */
	public void reprovar(OrdemServico ordemServico) {
		SagaOrdemServico saga = travarAguardandoAprovacao(ordemServico, "reprovar");

		statusService.cancelar(ordemServico, OrdemServicoStatusService.ORIGEM_API);
		saga.encerrar(SituacaoSaga.CANCELADA, MOTIVO_REPROVACAO_API);
		registrar(saga, "ReprovacaoApi");
	}

	/** Pago: a OS vai para a fila da Execução. */
	public void pagamentoConfirmado(EnvelopeMensagem evento) {
		Contexto contexto = carregar(evento, SituacaoSaga.EM_ANDAMENTO, EtapaSaga.AGUARDANDO_PAGAMENTO);
		long pagamentoId = numeroObrigatorio(evento, "pagamentoId");
		OrdemServico ordemServico = contexto.ordemServico();

		statusService.registrarPagamento(ordemServico, pagamentoId, evento.tipo());
		UUID comando = outboxService.registrar(EventoSaida.ENFILEIRAR_EXECUCAO, ordemServico.getId(),
				ordemServico.getCodigo(), contexto.saga().getSagaId(), EnfileirarExecucaoDados.de(
						itemOrdemServicoGateway.findByOrdemServicoId(ordemServico.getId()),
						servicoOrdemServicoGateway.findByOrdemServicoId(ordemServico.getId())));
		contexto.saga().avancar(EtapaSaga.AGUARDANDO_EXECUCAO, comando, null);
		registrar(contexto.saga(), evento.tipo());
	}

	public void pagamentoRecusado(EnvelopeMensagem evento) {
		Contexto contexto = carregar(evento, SituacaoSaga.EM_ANDAMENTO, EtapaSaga.AGUARDANDO_PAGAMENTO);

		statusService.cancelar(contexto.ordemServico(), evento.tipo());
		contexto.saga().encerrar(SituacaoSaga.CANCELADA, motivo(evento, "Pagamento recusado"));
		registrar(contexto.saga(), evento.tipo());
	}

	/**
	 * Falta peça: a OS espera o estoque. A Execução pode repetir o aviso (a lista de itens em falta
	 * muda), e a repetição não mexe mais no status.
	 */
	public void execucaoAguardandoItens(EnvelopeMensagem evento) {
		Contexto contexto = carregar(evento, SituacaoSaga.EM_ANDAMENTO,
				EtapaSaga.AGUARDANDO_EXECUCAO, EtapaSaga.AGUARDANDO_ITENS);

		if (contexto.saga().getEtapa() == EtapaSaga.AGUARDANDO_EXECUCAO) {
			statusService.aguardarItens(contexto.ordemServico(), evento.tipo());
			contexto.saga().avancar(EtapaSaga.AGUARDANDO_ITENS, null, null);
		}
		registrar(contexto.saga(), evento.tipo());
	}

	public void execucaoIniciada(EnvelopeMensagem evento) {
		Contexto contexto = carregar(evento, SituacaoSaga.EM_ANDAMENTO,
				EtapaSaga.AGUARDANDO_EXECUCAO, EtapaSaga.AGUARDANDO_ITENS);

		statusService.iniciarExecucao(contexto.ordemServico(), evento.tipo());
		contexto.saga().avancar(EtapaSaga.EM_EXECUCAO, null, null);
		registrar(contexto.saga(), evento.tipo());
	}

	/** Um serviço da OS terminou. Informativo: a OS só muda de status em {@code ExecucaoFinalizada}. */
	public void servicoFinalizado(EnvelopeMensagem evento) {
		Contexto contexto = carregar(evento, SituacaoSaga.EM_ANDAMENTO, EtapaSaga.EM_EXECUCAO);

		registrar(contexto.saga(), evento.tipo());
	}

	/**
	 * Compensação: a Execução não consegue concluir (peça danificada, sem reposição, por exemplo), na
	 * fila, esperando peças ou no meio do reparo. O pagamento é estornado; a OS só é cancelada quando
	 * o Billing confirmar o estorno.
	 */
	public void execucaoFalhou(EnvelopeMensagem evento) {
		Contexto contexto = carregar(evento, SituacaoSaga.EM_ANDAMENTO,
				EtapaSaga.AGUARDANDO_EXECUCAO, EtapaSaga.AGUARDANDO_ITENS, EtapaSaga.EM_EXECUCAO);
		String motivo = motivo(evento, "Execução recusada");

		solicitarEstorno(contexto, motivo);
		registrar(contexto.saga(), evento.tipo());
	}

	public void execucaoFinalizada(EnvelopeMensagem evento) {
		Contexto contexto = carregar(evento, SituacaoSaga.EM_ANDAMENTO, EtapaSaga.EM_EXECUCAO);

		statusService.finalizar(contexto.ordemServico(), evento.tipo());
		contexto.saga().encerrar(SituacaoSaga.CONCLUIDA, null);
		registrar(contexto.saga(), evento.tipo());
	}

	/** Fim da compensação: estorno confirmado, a OS é cancelada. */
	public void pagamentoEstornado(EnvelopeMensagem evento) {
		Contexto contexto = carregar(evento, SituacaoSaga.COMPENSANDO, EtapaSaga.AGUARDANDO_ESTORNO);

		statusService.cancelarAposEstorno(contexto.ordemServico(), evento.tipo());
		contexto.saga().encerrar(SituacaoSaga.COMPENSADA, null);
		registrar(contexto.saga(), evento.tipo());
	}

	/**
	 * Cancelamento pedido pela API. Sem pagamento, a OS é cancelada na hora e a Saga, se houver, é
	 * encerrada. Com pagamento, a Execução é avisada para devolver o estoque e o estorno é pedido ao
	 * Billing; a OS só é cancelada quando o estorno for confirmado.
	 *
	 * @return {@code true} se a OS foi cancelada agora; {@code false} se o cancelamento depende do estorno
	 */
	public boolean cancelar(OrdemServico ordemServico) {
		SagaOrdemServico saga = sagaRepository.travarPorOrdemServico(ordemServico.getId()).orElse(null);
		if (saga != null && saga.getSituacao() == SituacaoSaga.COMPENSANDO) {
			throw new RegraNegocioException("O cancelamento da ordem de serviço já está em andamento, aguardando o estorno");
		}

		if (saga != null && saga.getSituacao() == SituacaoSaga.EM_ANDAMENTO
				&& saga.getEtapa() == EtapaSaga.AGUARDANDO_PAGAMENTO) {
			// A cobrança já foi pedida e pode se confirmar a qualquer momento: cancelar agora deixaria
			// um pagamento cobrado sem estorno.
			throw new RegraNegocioException(PAYMENT_IN_PROGRESS);
		}

		if (ordemServico.getPagamentoId() == null) {
			statusService.cancelar(ordemServico, OrdemServicoStatusService.ORIGEM_API);
			if (saga != null && saga.getSituacao().ativa()) {
				saga.encerrar(SituacaoSaga.CANCELADA, MOTIVO_CANCELAMENTO_API);
				registrar(saga, "CancelamentoApi");
			}
			return true;
		}

		statusService.validarCancelavel(ordemServico);
		if (saga == null) {
			throw new RegraNegocioException("Ordem de serviço paga sem Saga registrada: cancelamento exige intervenção manual");
		}
		outboxService.registrar(EventoSaida.CANCELAR_EXECUCAO, ordemServico.getId(),
				ordemServico.getCodigo(), saga.getSagaId(),
				CancelarExecucaoDados.de(MOTIVO_CANCELAMENTO_API));
		solicitarEstorno(new Contexto(saga, ordemServico), MOTIVO_CANCELAMENTO_API);
		registrar(saga, "CancelamentoApi");
		return false;
	}

	/** Saga da OS travada, exigindo que ela esteja esperando a decisão do cliente sobre o orçamento. */
	private SagaOrdemServico travarAguardandoAprovacao(OrdemServico ordemServico, String acao) {
		SagaOrdemServico saga = sagaRepository.travarPorOrdemServico(ordemServico.getId())
				.orElseThrow(() -> new RegraNegocioException(
						"Não é possível %s: a ordem de serviço não tem orçamento solicitado".formatted(acao)));
		if (saga.getSituacao() != SituacaoSaga.EM_ANDAMENTO || saga.getEtapa() != EtapaSaga.AGUARDANDO_APROVACAO) {
			throw new RegraNegocioException(
					"Não é possível %s: a ordem de serviço não está aguardando a decisão sobre o orçamento (Saga em %s)"
							.formatted(acao, saga.getEtapa()));
		}
		return saga;
	}

	private void solicitarEstorno(Contexto contexto, String motivo) {
		UUID comando = outboxService.registrar(EventoSaida.ESTORNAR_PAGAMENTO, contexto.ordemServico().getId(),
				contexto.ordemServico().getCodigo(), contexto.saga().getSagaId(),
				EstornarPagamentoDados.de(contexto.ordemServico(), motivo));
		contexto.saga().compensar(EtapaSaga.AGUARDANDO_ESTORNO, comando, propriedades.prazoEstorno(), motivo);
	}

	/**
	 * Trava a Saga da OS do evento e confere se ela espera este evento. Evento de outra Saga (ciclo
	 * antigo), fora de ordem ou repetido depois do fim da Saga é recusado e vai para a DLQ, sem mexer
	 * na OS.
	 */
	private Contexto carregar(EnvelopeMensagem evento, SituacaoSaga situacaoEsperada, EtapaSaga... etapasEsperadas) {
		if (evento.ordemServicoId() == null) {
			throw new MensagemInvalidaException("Evento " + evento.tipo() + " sem ordemServicoId");
		}
		SagaOrdemServico saga = sagaRepository.travarPorOrdemServico(evento.ordemServicoId())
				.orElseThrow(() -> new MensagemInvalidaException(
						"Ordem de serviço " + evento.ordemServicoId() + " sem Saga registrada"));
		if (evento.sagaId() != null && !evento.sagaId().equals(saga.getSagaId())) {
			// Resposta atrasada de um ciclo anterior (a OS pediu orçamento de novo): aplicá-la contaminaria
			// o ciclo atual, por exemplo com o orçamentoId errado.
			throw new RegraNegocioException("Evento %s é da Saga %s, mas a Saga atual da OS é %s"
					.formatted(evento.tipo(), evento.sagaId(), saga.getSagaId()));
		}
		if (!List.of(etapasEsperadas).contains(saga.getEtapa()) || saga.getSituacao() != situacaoEsperada) {
			throw new RegraNegocioException("Evento %s não esperado: a Saga está em %s (%s)"
					.formatted(evento.tipo(), saga.getEtapa(), saga.getSituacao()));
		}
		OrdemServico ordemServico = ordemServicoGateway.findById(evento.ordemServicoId())
				.orElseThrow(() -> new MensagemInvalidaException(
						"Ordem de serviço " + evento.ordemServicoId() + " não encontrada"));
		return new Contexto(saga, ordemServico);
	}

	private static long numeroObrigatorio(EnvelopeMensagem evento, String campo) {
		JsonNode valor = evento.dados() == null ? null : evento.dados().get(campo);
		if (valor == null || !valor.isIntegralNumber()) {
			throw new MensagemInvalidaException("Evento %s sem o campo numérico %s".formatted(evento.tipo(), campo));
		}
		return valor.asLong();
	}

	private static String motivo(EnvelopeMensagem evento, String padrao) {
		JsonNode valor = evento.dados() == null ? null : evento.dados().get("motivo");
		return valor == null || valor.isNull() || valor.asString().isBlank() ? padrao : valor.asString();
	}

	private void registrar(SagaOrdemServico saga, String gatilho) {
		observabilidade.registrarTransicao(saga, gatilho);
	}

	/** Saga travada e OS do evento. */
	record Contexto(SagaOrdemServico saga, OrdemServico ordemServico) {
	}
}
