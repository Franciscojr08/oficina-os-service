package br.com.oficina.os.ordemservico.application;

import static br.com.oficina.os.shared.exception.ExceptionMessage.CANNOT_CANCEL_PAID_SERVICE_ORDER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.oficina.os.mensageria.evento.EventoSaida;
import br.com.oficina.os.mensageria.outbox.OutboxService;
import br.com.oficina.os.ordemservico.application.evento.OrdemServicoStatusAlteradoDados;
import br.com.oficina.os.ordemservico.application.gateway.HistoricoOrdemServicoGateway;
import br.com.oficina.os.ordemservico.application.gateway.OrdemServicoGateway;
import br.com.oficina.os.ordemservico.domain.HistoricoOrdemServico;
import br.com.oficina.os.ordemservico.domain.OrdemServico;
import br.com.oficina.os.saga.domain.SagaOrdemServico;
import br.com.oficina.os.saga.domain.SagaOrdemServicoRepository;
import br.com.oficina.os.saga.domain.SituacaoSaga;
import br.com.oficina.os.shared.exception.RegraNegocioException;
import br.com.oficina.os.shared.observabilidade.OrdemServicoObservabilidade;
import br.com.oficina.os.shared.observabilidade.TelemetriaEmMemoria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Cobre a máquina de estados da OS: cada transição permitida (status de destino, data carimbada,
 * histórico com origem) e a recusa de toda transição a partir de um status não permitido.
 */
@ExtendWith(MockitoExtension.class)
class OrdemServicoStatusServiceTest {

	private static final String ORIGEM_EVENTO = "EventoDeTeste";

	@Mock
	private OrdemServicoGateway ordemServicoGateway;

	@Mock
	private HistoricoOrdemServicoGateway historicoGateway;

	@Mock
	private OutboxService outboxService;

	@Mock
	private SagaOrdemServicoRepository sagaRepository;

	private OrdemServicoStatusService statusService;

	@BeforeEach
	void setUp() {
		statusService = new OrdemServicoStatusService(
				ordemServicoGateway,
				new HistoricoOrdemServicoService(historicoGateway),
				new OrdemServicoObservabilidade(new TelemetriaEmMemoria()),
				outboxService,
				sagaRepository
		);
	}

	@Test
	void deveDefinirStatusInicialSemGravar() {
		OrdemServico os = new OrdemServico();

		statusService.definirStatusInicial(os);

		assertThat(os.getStatus()).isEqualTo(StatusOrdemServico.RECEBIDA);
		verify(ordemServicoGateway, never()).saveAndFlush(any());
	}

	@Test
	void deveIniciarDiagnostico() {
		OrdemServico os = executar(StatusOrdemServico.RECEBIDA, statusService::iniciarDiagnostico);

		assertThat(os.getStatus()).isEqualTo(StatusOrdemServico.EM_DIAGNOSTICO);
		assertHistorico(StatusOrdemServico.EM_DIAGNOSTICO, OrdemServicoStatusService.ORIGEM_API);
	}

	@Test
	void deveSolicitarOrcamentoSemMudarOStatus() {
		OrdemServico os = executar(StatusOrdemServico.EM_DIAGNOSTICO, statusService::solicitarOrcamento);

		assertThat(os.getStatus()).as("a espera do orçamento é etapa da Saga, não status")
				.isEqualTo(StatusOrdemServico.EM_DIAGNOSTICO);
		assertThat(os.getDataSolicitacaoOrcamento()).isNotNull();
		verify(historicoGateway, never()).save(any());
		verify(outboxService, never()).registrar(any(), any(), any(), any(), any());
	}

	@Test
	void deveRegistrarOrcamentoGeradoComId() {
		OrdemServico os = executar(StatusOrdemServico.EM_DIAGNOSTICO,
				o -> statusService.registrarOrcamentoGerado(o, 77L, ORIGEM_EVENTO));

		assertThat(os.getStatus()).isEqualTo(StatusOrdemServico.AGUARDANDO_APROVACAO);
		assertThat(os.getOrcamentoId()).isEqualTo(77L);
		assertThat(os.getDataEnvioAprovacao()).isNotNull();
		assertHistorico(StatusOrdemServico.AGUARDANDO_APROVACAO, ORIGEM_EVENTO);
	}

	@Test
	void orcamentoNaoGeradoSoRegistraOMotivoNoHistorico() {
		OrdemServico os = criarOs(StatusOrdemServico.EM_DIAGNOSTICO);

		statusService.registrarOrcamentoNaoGerado(os, ORIGEM_EVENTO);

		assertThat(os.getStatus()).isEqualTo(StatusOrdemServico.EM_DIAGNOSTICO);
		ArgumentCaptor<HistoricoOrdemServico> captor = ArgumentCaptor.forClass(HistoricoOrdemServico.class);
		verify(historicoGateway).save(captor.capture());
		assertThat(captor.getValue().getOrigem()).isEqualTo(ORIGEM_EVENTO);
	}

	@Test
	void deveRegistrarAprovacaoSemMudarOStatus() {
		OrdemServico os = executar(StatusOrdemServico.AGUARDANDO_APROVACAO, statusService::registrarAprovacao);

		assertThat(os.getStatus()).as("só vira APROVADA com o pagamento confirmado")
				.isEqualTo(StatusOrdemServico.AGUARDANDO_APROVACAO);
		assertThat(os.getDataAprovacao()).isNotNull();
	}

	@Test
	void deveRegistrarPagamentoComId() {
		OrdemServico os = executar(StatusOrdemServico.AGUARDANDO_APROVACAO,
				o -> statusService.registrarPagamento(o, 88L, ORIGEM_EVENTO));

		assertThat(os.getStatus()).isEqualTo(StatusOrdemServico.APROVADA);
		assertThat(os.getPagamentoId()).isEqualTo(88L);
		assertThat(os.getDataPagamento()).isNotNull();
	}

	@Test
	void deveAguardarItens() {
		OrdemServico os = executar(StatusOrdemServico.APROVADA, o -> statusService.aguardarItens(o, ORIGEM_EVENTO));

		assertThat(os.getStatus()).isEqualTo(StatusOrdemServico.AGUARDANDO_ITENS);
		assertHistorico(StatusOrdemServico.AGUARDANDO_ITENS, ORIGEM_EVENTO);
	}

	@Test
	void deveIniciarExecucaoDaAprovacaoOuDepoisDeEsperarPecas() {
		for (StatusOrdemServico origem : new StatusOrdemServico[] {
				StatusOrdemServico.APROVADA, StatusOrdemServico.AGUARDANDO_ITENS}) {
			OrdemServico os = criarOs(origem);

			statusService.iniciarExecucao(os, ORIGEM_EVENTO);

			assertThat(os.getStatus()).isEqualTo(StatusOrdemServico.EM_EXECUCAO);
			assertThat(os.getDataInicioExecucao()).isNotNull();
		}
	}

	@Test
	void deveFinalizar() {
		OrdemServico os = executar(StatusOrdemServico.EM_EXECUCAO,
				o -> {
					o.setDataInicioExecucao(LocalDateTime.now().minusHours(2));
					statusService.finalizar(o, ORIGEM_EVENTO);
				});

		assertThat(os.getStatus()).isEqualTo(StatusOrdemServico.FINALIZADA);
		assertThat(os.getDataFimExecucao()).isNotNull();
	}

	@Test
	void deveEntregar() {
		OrdemServico os = executar(StatusOrdemServico.FINALIZADA,
				o -> {
					o.setDataFimExecucao(LocalDateTime.now().minusHours(1));
					statusService.entregar(o);
				});

		assertThat(os.getStatus()).isEqualTo(StatusOrdemServico.ENTREGUE);
		assertThat(os.getDataEntregue()).isNotNull();
	}

	@ParameterizedTest
	@EnumSource(value = StatusOrdemServico.class, names = {
			"RECEBIDA", "EM_DIAGNOSTICO", "AGUARDANDO_APROVACAO", "APROVADA", "AGUARDANDO_ITENS"
	})
	void deveCancelarAntesDaExecucao(StatusOrdemServico statusAtual) {
		OrdemServico os = executar(statusAtual, o -> statusService.cancelar(o, ORIGEM_EVENTO));

		assertThat(os.getStatus()).isEqualTo(StatusOrdemServico.CANCELADA);
		assertThat(os.getDataCancelada()).isNotNull();
		assertHistorico(StatusOrdemServico.CANCELADA, ORIGEM_EVENTO);
	}

	@ParameterizedTest
	@EnumSource(value = StatusOrdemServico.class, names = {"EM_EXECUCAO", "FINALIZADA", "ENTREGUE", "CANCELADA"})
	void naoDeveCancelarDepoisQueAExecucaoComecou(StatusOrdemServico statusAtual) {
		assertRecusa(statusAtual, o -> statusService.cancelar(o, ORIGEM_EVENTO));
	}

	@Test
	void estornoCancelaAteUmaOsQueJaEstavaEmExecucao() {
		OrdemServico os = executar(StatusOrdemServico.EM_EXECUCAO, o -> statusService.cancelarAposEstorno(o, ORIGEM_EVENTO));

		assertThat(os.getStatus()).isEqualTo(StatusOrdemServico.CANCELADA);
	}

	@ParameterizedTest
	@EnumSource(value = StatusOrdemServico.class, names = {"FINALIZADA", "ENTREGUE", "CANCELADA"})
	void estornoNaoCancelaOsEncerrada(StatusOrdemServico statusAtual) {
		assertRecusa(statusAtual, o -> statusService.cancelarAposEstorno(o, ORIGEM_EVENTO));
	}

	@ParameterizedTest
	@EnumSource(value = StatusOrdemServico.class, names = "RECEBIDA", mode = EnumSource.Mode.EXCLUDE)
	void naoDeveIniciarDiagnosticoForaDeRecebida(StatusOrdemServico statusAtual) {
		assertRecusa(statusAtual, statusService::iniciarDiagnostico);
	}

	@ParameterizedTest
	@EnumSource(value = StatusOrdemServico.class, names = "EM_DIAGNOSTICO", mode = EnumSource.Mode.EXCLUDE)
	void naoDeveSolicitarOrcamentoForaDoDiagnostico(StatusOrdemServico statusAtual) {
		assertRecusa(statusAtual, statusService::solicitarOrcamento);
	}

	@ParameterizedTest
	@EnumSource(value = StatusOrdemServico.class, names = "EM_DIAGNOSTICO", mode = EnumSource.Mode.EXCLUDE)
	void naoDeveRegistrarOrcamentoForaDoDiagnostico(StatusOrdemServico statusAtual) {
		assertRecusa(statusAtual, o -> statusService.registrarOrcamentoGerado(o, 1L, ORIGEM_EVENTO));
	}

	@ParameterizedTest
	@EnumSource(value = StatusOrdemServico.class, names = "EM_DIAGNOSTICO", mode = EnumSource.Mode.EXCLUDE)
	void naoDeveRegistrarOrcamentoNaoGeradoForaDoDiagnostico(StatusOrdemServico statusAtual) {
		assertRecusa(statusAtual, o -> statusService.registrarOrcamentoNaoGerado(o, ORIGEM_EVENTO));
	}

	@ParameterizedTest
	@EnumSource(value = StatusOrdemServico.class, names = "AGUARDANDO_APROVACAO", mode = EnumSource.Mode.EXCLUDE)
	void naoDeveRegistrarAprovacaoForaDeAguardandoAprovacao(StatusOrdemServico statusAtual) {
		assertRecusa(statusAtual, statusService::registrarAprovacao);
	}

	@ParameterizedTest
	@EnumSource(value = StatusOrdemServico.class, names = "AGUARDANDO_APROVACAO", mode = EnumSource.Mode.EXCLUDE)
	void naoDeveRegistrarPagamentoForaDeAguardandoAprovacao(StatusOrdemServico statusAtual) {
		assertRecusa(statusAtual, o -> statusService.registrarPagamento(o, 1L, ORIGEM_EVENTO));
	}

	@ParameterizedTest
	@EnumSource(value = StatusOrdemServico.class, names = "APROVADA", mode = EnumSource.Mode.EXCLUDE)
	void naoDeveAguardarItensForaDeAprovada(StatusOrdemServico statusAtual) {
		assertRecusa(statusAtual, o -> statusService.aguardarItens(o, ORIGEM_EVENTO));
	}

	@ParameterizedTest
	@EnumSource(value = StatusOrdemServico.class, names = {"APROVADA", "AGUARDANDO_ITENS"}, mode = EnumSource.Mode.EXCLUDE)
	void naoDeveIniciarExecucaoForaDeAprovadaOuAguardandoItens(StatusOrdemServico statusAtual) {
		assertRecusa(statusAtual, o -> statusService.iniciarExecucao(o, ORIGEM_EVENTO));
	}

	@ParameterizedTest
	@EnumSource(value = StatusOrdemServico.class, names = "EM_EXECUCAO", mode = EnumSource.Mode.EXCLUDE)
	void naoDeveFinalizarForaDeEmExecucao(StatusOrdemServico statusAtual) {
		assertRecusa(statusAtual, o -> statusService.finalizar(o, ORIGEM_EVENTO));
	}

	@ParameterizedTest
	@EnumSource(value = StatusOrdemServico.class, names = "FINALIZADA", mode = EnumSource.Mode.EXCLUDE)
	void naoDeveEntregarForaDeFinalizada(StatusOrdemServico statusAtual) {
		assertRecusa(statusAtual, statusService::entregar);
	}

	@Test
	void naoDeveCancelarOrdemComPagamentoRegistradoSemEstorno() {
		OrdemServico os = criarOs(StatusOrdemServico.APROVADA);
		os.setPagamentoId(10L);

		assertThatThrownBy(() -> statusService.cancelar(os, ORIGEM_EVENTO))
				.isInstanceOf(RegraNegocioException.class)
				.hasMessage(CANNOT_CANCEL_PAID_SERVICE_ORDER);

		assertThat(os.getStatus()).isEqualTo(StatusOrdemServico.APROVADA);
		assertThat(os.getDataCancelada()).isNull();
		verify(ordemServicoGateway, never()).saveAndFlush(any());
		verify(historicoGateway, never()).save(any());
	}

	@ParameterizedTest
	@EnumSource(value = StatusOrdemServico.class, names = {"RECEBIDA", "EM_DIAGNOSTICO"})
	void devePermitirAlteracaoAntesDoPedidoDeOrcamento(StatusOrdemServico status) {
		OrdemServico os = criarOs(status);

		statusService.validarPermiteAlteracao(os);

		assertThat(os.getStatus()).isEqualTo(status);
	}

	@ParameterizedTest
	@EnumSource(value = StatusOrdemServico.class, names = {"RECEBIDA", "EM_DIAGNOSTICO"}, mode = EnumSource.Mode.EXCLUDE)
	void naoDevePermitirAlteracaoDepoisDoPedidoDeOrcamento(StatusOrdemServico status) {
		OrdemServico os = criarOs(status);

		assertThatThrownBy(() -> statusService.validarPermiteAlteracao(os))
				.isInstanceOf(RegraNegocioException.class)
				.hasMessageContaining("alterar a ordem de serviço")
				.hasMessageContaining(status.getDescricao());
	}

	@Test
	void validarEmDiagnosticoDeveRecusarOutroStatus() {
		OrdemServico os = criarOs(StatusOrdemServico.RECEBIDA);

		assertThatThrownBy(() -> statusService.validarEmDiagnostico(os, "mensagem"))
				.isInstanceOf(RegraNegocioException.class)
				.hasMessage("mensagem");
	}

	@Test
	void naoDeveAlterarEnquantoOOrcamentoEstaSendoGerado() {
		OrdemServico os = criarOs(StatusOrdemServico.EM_DIAGNOSTICO);
		when(sagaRepository.findByOrdemServicoId(1L)).thenReturn(Optional.of(saga(SituacaoSaga.EM_ANDAMENTO)));

		assertThatThrownBy(() -> statusService.validarEmDiagnostico(os, "mensagem"))
				.isInstanceOf(RegraNegocioException.class)
				.hasMessageContaining("orçamento está sendo gerado");
		assertThatThrownBy(() -> statusService.validarPermiteAlteracao(os))
				.isInstanceOf(RegraNegocioException.class)
				.hasMessageContaining("orçamento está sendo gerado");
	}

	@Test
	void devePermitirAlterarDepoisQueASagaDoOrcamentoSeEncerrou() {
		OrdemServico os = criarOs(StatusOrdemServico.EM_DIAGNOSTICO);
		when(sagaRepository.findByOrdemServicoId(1L)).thenReturn(Optional.of(saga(SituacaoSaga.COMPENSADA)));

		statusService.validarEmDiagnostico(os, "mensagem");
		statusService.validarPermiteAlteracao(os);
	}

	private static SagaOrdemServico saga(SituacaoSaga situacao) {
		SagaOrdemServico saga = SagaOrdemServico.para(1L);
		saga.setSituacao(situacao);
		return saga;
	}

	@Test
	void validarEmDiagnosticoDeveAceitarDiagnostico() {
		OrdemServico os = criarOs(StatusOrdemServico.EM_DIAGNOSTICO);

		statusService.validarEmDiagnostico(os, "mensagem");

		assertThat(os.getStatus()).isEqualTo(StatusOrdemServico.EM_DIAGNOSTICO);
	}

	@Test
	void statusAtivosNaoIncluemEstadosFinais() {
		assertThat(StatusOrdemServico.statusAtivos())
				.doesNotContain(StatusOrdemServico.ENTREGUE, StatusOrdemServico.CANCELADA)
				.hasSize(StatusOrdemServico.values().length - 2);
		assertThat(StatusOrdemServico.values()).as("os 9 status do contrato").hasSize(9);
	}

	private OrdemServico executar(StatusOrdemServico statusAtual, Consumer<OrdemServico> acao) {
		OrdemServico os = criarOs(statusAtual);
		acao.accept(os);
		verify(ordemServicoGateway).saveAndFlush(os);
		return os;
	}

	private void assertRecusa(StatusOrdemServico statusAtual, Consumer<OrdemServico> acao) {
		OrdemServico os = criarOs(statusAtual);

		assertThatThrownBy(() -> acao.accept(os))
				.isInstanceOf(RegraNegocioException.class)
				.hasMessageContaining(statusAtual.getDescricao());

		assertThat(os.getStatus()).isEqualTo(statusAtual);
		verify(ordemServicoGateway, never()).saveAndFlush(any());
		verify(historicoGateway, never()).save(any());
		verify(outboxService, never()).registrar(any(), any(), any(), any(), any());
	}

	/** Toda transição grava histórico e o evento OrdemServicoStatusAlterado, com a mesma origem. */
	private void assertHistorico(StatusOrdemServico status, String origem) {
		ArgumentCaptor<HistoricoOrdemServico> captor = ArgumentCaptor.forClass(HistoricoOrdemServico.class);
		verify(historicoGateway).save(captor.capture());
		assertThat(captor.getValue().getStatus()).isEqualTo(status);
		assertThat(captor.getValue().getOrigem()).isEqualTo(origem);
		assertThat(captor.getValue().getObservacao()).isEqualTo(status.getDescricao());

		ArgumentCaptor<Object> evento = ArgumentCaptor.forClass(Object.class);
		verify(outboxService).registrar(eq(EventoSaida.ORDEM_SERVICO_STATUS_ALTERADO), eq(1L), any(), isNull(), evento.capture());
		OrdemServicoStatusAlteradoDados dados = (OrdemServicoStatusAlteradoDados) evento.getValue();
		assertThat(dados.statusAtual()).isEqualTo(status);
		assertThat(dados.origem()).isEqualTo(origem);
		assertThat(dados.codigo()).isEqualTo("OS-2026-000001");
		assertThat(dados.alteradoEm()).isNotNull();
	}

	@Test
	void registrarAberturaDeveGravarHistoricoEEventoSemStatusAnterior() {
		OrdemServico os = criarOs(StatusOrdemServico.RECEBIDA);

		statusService.registrarAbertura(os);

		ArgumentCaptor<Object> evento = ArgumentCaptor.forClass(Object.class);
		verify(outboxService).registrar(eq(EventoSaida.ORDEM_SERVICO_STATUS_ALTERADO), eq(1L), any(), isNull(), evento.capture());
		OrdemServicoStatusAlteradoDados dados = (OrdemServicoStatusAlteradoDados) evento.getValue();
		assertThat(dados.statusAnterior()).isNull();
		assertThat(dados.statusAtual()).isEqualTo(StatusOrdemServico.RECEBIDA);
		verify(historicoGateway).save(any(HistoricoOrdemServico.class));
		verify(ordemServicoGateway, never()).saveAndFlush(any());
	}

	private OrdemServico criarOs(StatusOrdemServico status) {
		OrdemServico os = new OrdemServico();
		os.setId(1L);
		os.setCodigo("OS-2026-000001");
		os.setStatus(status);
		os.setDataCadastro(LocalDateTime.now().minusDays(1));
		return os;
	}
}
