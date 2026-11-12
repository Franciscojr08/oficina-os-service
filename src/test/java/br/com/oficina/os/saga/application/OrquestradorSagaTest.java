package br.com.oficina.os.saga.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.oficina.os.cliente.domain.Cliente;
import br.com.oficina.os.mensageria.consumo.MensagemInvalidaException;
import br.com.oficina.os.mensageria.evento.EnvelopeMensagem;
import br.com.oficina.os.mensageria.evento.EventoSaida;
import br.com.oficina.os.mensageria.outbox.OutboxService;
import br.com.oficina.os.ordemservico.application.HistoricoOrdemServicoService;
import br.com.oficina.os.ordemservico.application.OrdemServicoStatusService;
import br.com.oficina.os.ordemservico.application.StatusOrdemServico;
import br.com.oficina.os.ordemservico.application.gateway.HistoricoOrdemServicoGateway;
import br.com.oficina.os.ordemservico.application.gateway.OrdemServicoGateway;
import br.com.oficina.os.ordemservico.domain.HistoricoOrdemServico;
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
import br.com.oficina.os.saga.evento.CobrarPagamentoDados;
import br.com.oficina.os.saga.evento.EnfileirarExecucaoDados;
import br.com.oficina.os.saga.evento.EstornarPagamentoDados;
import br.com.oficina.os.shared.exception.RegraNegocioException;
import br.com.oficina.os.shared.observabilidade.OrdemServicoObservabilidade;
import br.com.oficina.os.shared.observabilidade.TelemetriaEmMemoria;
import br.com.oficina.os.veiculo.domain.Veiculo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Cada evento da Saga com a máquina de estados real: status da OS, histórico com a origem, estado
 * da Saga e comandos publicados. Banco e broker são simulados.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrquestradorSagaTest {

	private static final long OS_ID = 42L;
	private static final UUID COMANDO = UUID.randomUUID();
	private static final UUID SAGA_ID = UUID.randomUUID();

	@Mock
	private SagaOrdemServicoRepository sagaRepository;

	@Mock
	private OrdemServicoGateway ordemServicoGateway;

	@Mock
	private ItemOrdemServicoGateway itemOrdemServicoGateway;

	@Mock
	private ServicoOrdemServicoGateway servicoOrdemServicoGateway;

	@Mock
	private HistoricoOrdemServicoGateway historicoGateway;

	@Mock
	private OutboxService outboxService;

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	private OrquestradorSaga orquestrador;
	private final TelemetriaEmMemoria telemetria = new TelemetriaEmMemoria();

	@BeforeEach
	void setUp() {
		OrdemServicoStatusService statusService = new OrdemServicoStatusService(
				ordemServicoGateway, new HistoricoOrdemServicoService(historicoGateway),
				new OrdemServicoObservabilidade(new TelemetriaEmMemoria()), outboxService, sagaRepository);
		SagaProperties propriedades = new SagaProperties(Duration.ofMinutes(5), Duration.ofMinutes(10), 3,
				new SagaProperties.Verificacao(false, 1000, 50));
		orquestrador = new OrquestradorSaga(sagaRepository, ordemServicoGateway, itemOrdemServicoGateway,
				servicoOrdemServicoGateway, statusService, outboxService, propriedades, new SagaObservabilidade(telemetria));
		when(outboxService.registrar(any(), any(), any(), any(), any())).thenReturn(COMANDO);
	}

	@Test
	void novoPedidoDeOrcamentoDepoisDeUmaSagaEncerradaGeraOutroSagaId() {
		SagaOrdemServico anterior = saga(EtapaSaga.AGUARDANDO_ORCAMENTO, SituacaoSaga.COMPENSADA);
		OrdemServico os = os(StatusOrdemServico.EM_DIAGNOSTICO);

		orquestrador.iniciar(os, List.of(item()), List.of(servico()));

		assertThat(anterior.getSagaId()).as("o ciclo novo não reaproveita o id do anterior").isNotEqualTo(SAGA_ID).isNotNull();
		verify(outboxService).registrar(eq(EventoSaida.SOLICITAR_ORCAMENTO), eq(OS_ID), eq("OS-2026-000042"),
				eq(anterior.getSagaId()), any());
	}

	@Test
	void iniciarDeveCriarASagaEPedirOOrcamento() {
		OrdemServico os = os(StatusOrdemServico.EM_DIAGNOSTICO);
		when(sagaRepository.travarPorOrdemServico(OS_ID)).thenReturn(Optional.empty());

		orquestrador.iniciar(os, List.of(item()), List.of(servico()));

		ArgumentCaptor<SagaOrdemServico> salva = ArgumentCaptor.forClass(SagaOrdemServico.class);
		verify(sagaRepository).save(salva.capture());
		SagaOrdemServico saga = salva.getValue();
		assertThat(saga.getOrdemServicoId()).isEqualTo(OS_ID);
		assertThat(saga.getEtapa()).isEqualTo(EtapaSaga.AGUARDANDO_ORCAMENTO);
		assertThat(saga.getSituacao()).isEqualTo(SituacaoSaga.EM_ANDAMENTO);
		assertThat(saga.getComandoMessageId()).isEqualTo(COMANDO);
		assertThat(saga.getPrazo()).isBetween(LocalDateTime.now().plusMinutes(4), LocalDateTime.now().plusMinutes(6));
		assertThat(saga.getIniciadaEm()).isNotNull();
		assertThat(saga.getSagaId()).isNotNull();
		verify(outboxService).registrar(eq(EventoSaida.SOLICITAR_ORCAMENTO), eq(OS_ID), eq("OS-2026-000042"),
				eq(saga.getSagaId()), any());
		assertThat(telemetria.eventos(SagaObservabilidade.EVENTO_TRANSICAO)).singleElement()
				.satisfies(evento -> assertThat(evento.atributos())
						.containsEntry("gatilho", "SolicitarOrcamento")
						.containsEntry("etapa", "AGUARDANDO_ORCAMENTO")
						.containsEntry("situacao", "EM_ANDAMENTO")
						.containsEntry("encerrada", false)
						.doesNotContainKey("duracaoMs"));
	}

	@Test
	void novoPedidoDeOrcamentoReiniciaUmaSagaEncerrada() {
		SagaOrdemServico saga = saga(EtapaSaga.AGUARDANDO_ORCAMENTO, SituacaoSaga.COMPENSADA);
		saga.setMotivo("Billing não gerou o orçamento");
		saga.setTentativas(2);

		orquestrador.iniciar(os(StatusOrdemServico.EM_DIAGNOSTICO), List.of(item()), List.of(servico()));

		assertThat(saga.getSituacao()).isEqualTo(SituacaoSaga.EM_ANDAMENTO);
		assertThat(saga.getMotivo()).isNull();
		assertThat(saga.getTentativas()).isZero();
		assertThat(saga.getEncerradaEm()).isNull();
	}

	@Test
	void naoDeveIniciarComSagaEmAndamento() {
		saga(EtapaSaga.AGUARDANDO_APROVACAO, SituacaoSaga.EM_ANDAMENTO);

		assertThatThrownBy(() -> orquestrador.iniciar(os(StatusOrdemServico.EM_DIAGNOSTICO), List.of(), List.of()))
				.isInstanceOf(RegraNegocioException.class);
		verify(outboxService, never()).registrar(any(), any(), any(), any(), any());
	}

	@Test
	void orcamentoGeradoLevaAOsParaAprovacao() {
		SagaOrdemServico saga = saga(EtapaSaga.AGUARDANDO_ORCAMENTO, SituacaoSaga.EM_ANDAMENTO);
		OrdemServico os = os(StatusOrdemServico.EM_DIAGNOSTICO);

		orquestrador.orcamentoGerado(evento("OrcamentoGerado", Map.of("orcamentoId", 77)));

		assertThat(os.getStatus()).isEqualTo(StatusOrdemServico.AGUARDANDO_APROVACAO);
		assertThat(os.getOrcamentoId()).isEqualTo(77L);
		assertThat(saga.getEtapa()).isEqualTo(EtapaSaga.AGUARDANDO_APROVACAO);
		assertThat(saga.getPrazo()).as("aprovação depende do cliente e não expira").isNull();
		assertHistorico(StatusOrdemServico.AGUARDANDO_APROVACAO, "OrcamentoGerado");
	}

	@Test
	void orcamentoGeradoSemOrcamentoIdEhMensagemInvalida() {
		saga(EtapaSaga.AGUARDANDO_ORCAMENTO, SituacaoSaga.EM_ANDAMENTO);
		OrdemServico os = os(StatusOrdemServico.EM_DIAGNOSTICO);

		assertThatThrownBy(() -> orquestrador.orcamentoGerado(evento("OrcamentoGerado", Map.of())))
				.isInstanceOf(MensagemInvalidaException.class)
				.hasMessageContaining("orcamentoId");
		assertThat(os.getStatus()).isEqualTo(StatusOrdemServico.EM_DIAGNOSTICO);
	}

	@Test
	void aprovarPedeACobrancaEAguardaOPagamento() {
		SagaOrdemServico saga = saga(EtapaSaga.AGUARDANDO_APROVACAO, SituacaoSaga.EM_ANDAMENTO);
		OrdemServico os = os(StatusOrdemServico.AGUARDANDO_APROVACAO);
		os.setOrcamentoId(77L);

		orquestrador.aprovar(os);

		assertThat(os.getStatus()).as("só vira APROVADA quando o pagamento for confirmado")
				.isEqualTo(StatusOrdemServico.AGUARDANDO_APROVACAO);
		assertThat(os.getDataAprovacao()).isNotNull();
		assertThat(saga.getEtapa()).isEqualTo(EtapaSaga.AGUARDANDO_PAGAMENTO);
		assertThat(saga.getComandoMessageId()).isEqualTo(COMANDO);
		assertThat(saga.getPrazo()).as("pagamento pode demorar (Pix) e não expira").isNull();
		ArgumentCaptor<Object> dados = ArgumentCaptor.forClass(Object.class);
		verify(outboxService).registrar(eq(EventoSaida.COBRAR_PAGAMENTO), eq(OS_ID), eq("OS-2026-000042"), eq(SAGA_ID), dados.capture());
		assertThat(((CobrarPagamentoDados) dados.getValue()).orcamentoId()).isEqualTo(77L);
	}

	@Test
	void aprovarForaDaEtapaDeAprovacaoEhRecusado() {
		saga(EtapaSaga.AGUARDANDO_PAGAMENTO, SituacaoSaga.EM_ANDAMENTO);
		OrdemServico os = os(StatusOrdemServico.AGUARDANDO_APROVACAO);

		assertThatThrownBy(() -> orquestrador.aprovar(os))
				.isInstanceOf(RegraNegocioException.class)
				.hasMessageContaining("AGUARDANDO_PAGAMENTO");
		verify(outboxService, never()).registrar(any(), any(), any(), any(), any());
	}

	@Test
	void aprovarSemOrcamentoSolicitadoEhRecusado() {
		when(sagaRepository.travarPorOrdemServico(OS_ID)).thenReturn(Optional.empty());
		OrdemServico os = os(StatusOrdemServico.EM_DIAGNOSTICO);

		assertThatThrownBy(() -> orquestrador.aprovar(os))
				.isInstanceOf(RegraNegocioException.class)
				.hasMessageContaining("não tem orçamento solicitado");
	}

	@Test
	void reprovarCancelaSemCompensar() {
		SagaOrdemServico saga = saga(EtapaSaga.AGUARDANDO_APROVACAO, SituacaoSaga.EM_ANDAMENTO);
		OrdemServico os = os(StatusOrdemServico.AGUARDANDO_APROVACAO);

		orquestrador.reprovar(os);

		assertThat(os.getStatus()).isEqualTo(StatusOrdemServico.CANCELADA);
		assertThat(saga.getSituacao()).isEqualTo(SituacaoSaga.CANCELADA);
		assertThat(saga.getMotivo()).isEqualTo("Orçamento reprovado pelo cliente");
		verify(outboxService, never()).registrar(eq(EventoSaida.ESTORNAR_PAGAMENTO), any(), any(), any(), any());
		verify(outboxService, never()).registrar(eq(EventoSaida.COBRAR_PAGAMENTO), any(), any(), any(), any());
	}

	@Test
	void reprovarComASagaEncerradaEhRecusado() {
		saga(EtapaSaga.AGUARDANDO_APROVACAO, SituacaoSaga.CANCELADA);
		OrdemServico os = os(StatusOrdemServico.CANCELADA);

		assertThatThrownBy(() -> orquestrador.reprovar(os)).isInstanceOf(RegraNegocioException.class);
	}

	@Test
	void pagamentoConfirmadoPedeAExecucao() {
		SagaOrdemServico saga = saga(EtapaSaga.AGUARDANDO_PAGAMENTO, SituacaoSaga.EM_ANDAMENTO);
		OrdemServico os = os(StatusOrdemServico.AGUARDANDO_APROVACAO);
		when(itemOrdemServicoGateway.findByOrdemServicoId(OS_ID)).thenReturn(List.of(item()));
		when(servicoOrdemServicoGateway.findByOrdemServicoId(OS_ID)).thenReturn(List.of(servico()));

		orquestrador.pagamentoConfirmado(evento("PagamentoConfirmado", Map.of("pagamentoId", 900)));

		assertThat(os.getStatus()).isEqualTo(StatusOrdemServico.APROVADA);
		assertThat(os.getPagamentoId()).isEqualTo(900L);
		assertThat(saga.getEtapa()).isEqualTo(EtapaSaga.AGUARDANDO_EXECUCAO);
		assertThat(saga.getComandoMessageId()).isEqualTo(COMANDO);

		ArgumentCaptor<Object> dados = ArgumentCaptor.forClass(Object.class);
		verify(outboxService).registrar(eq(EventoSaida.ENFILEIRAR_EXECUCAO), eq(OS_ID), eq("OS-2026-000042"), eq(SAGA_ID), dados.capture());
		EnfileirarExecucaoDados pedido = (EnfileirarExecucaoDados) dados.getValue();
		assertThat(pedido.itens()).singleElement().satisfies(i -> {
			assertThat(i.itemId()).isEqualTo(20L);
			assertThat(i.quantidade()).isEqualTo(2);
		});
		assertThat(pedido.servicos()).singleElement().satisfies(sv -> assertThat(sv.servicoId()).isEqualTo(30L));
	}

	@Test
	void pagamentoConfirmadoSemPagamentoIdEhMensagemInvalida() {
		saga(EtapaSaga.AGUARDANDO_PAGAMENTO, SituacaoSaga.EM_ANDAMENTO);
		os(StatusOrdemServico.AGUARDANDO_APROVACAO);

		assertThatThrownBy(() -> orquestrador.pagamentoConfirmado(evento("PagamentoConfirmado", Map.of("pagamentoId", "abc"))))
				.isInstanceOf(MensagemInvalidaException.class);
	}

	@Test
	void pagamentoRecusadoCancelaSemCompensar() {
		SagaOrdemServico saga = saga(EtapaSaga.AGUARDANDO_PAGAMENTO, SituacaoSaga.EM_ANDAMENTO);
		OrdemServico os = os(StatusOrdemServico.AGUARDANDO_APROVACAO);

		orquestrador.pagamentoRecusado(evento("PagamentoRecusado", Map.of("motivo", "Cartão recusado")));

		assertThat(os.getStatus()).isEqualTo(StatusOrdemServico.CANCELADA);
		assertThat(saga.getSituacao()).isEqualTo(SituacaoSaga.CANCELADA);
		assertThat(saga.getMotivo()).isEqualTo("Cartão recusado");
	}

	@Test
	void execucaoIniciadaEFinalizadaConcluemASaga() {
		SagaOrdemServico saga = saga(EtapaSaga.AGUARDANDO_EXECUCAO, SituacaoSaga.EM_ANDAMENTO);
		OrdemServico os = os(StatusOrdemServico.APROVADA);

		orquestrador.execucaoIniciada(evento("ExecucaoIniciada", Map.of()));
		assertThat(os.getStatus()).isEqualTo(StatusOrdemServico.EM_EXECUCAO);
		assertThat(saga.getEtapa()).isEqualTo(EtapaSaga.EM_EXECUCAO);

		saga.setIniciadaEm(LocalDateTime.now().minusMinutes(30));
		orquestrador.execucaoFinalizada(evento("ExecucaoFinalizada", Map.of()));
		assertThat(os.getStatus()).isEqualTo(StatusOrdemServico.FINALIZADA);
		assertThat(saga.getSituacao()).isEqualTo(SituacaoSaga.CONCLUIDA);
		assertThat(saga.getEncerradaEm()).isNotNull();

		// Encerramento: o evento leva a duração da Saga inteira
		assertThat(telemetria.eventos(SagaObservabilidade.EVENTO_TRANSICAO)).last()
				.satisfies(evento -> assertThat(evento.atributos())
						.containsEntry("gatilho", "ExecucaoFinalizada")
						.containsEntry("situacao", "CONCLUIDA")
						.containsEntry("encerrada", true)
						.hasEntrySatisfying("duracaoMs", duracao ->
								assertThat((Long) duracao).isBetween(29 * 60_000L, 31 * 60_000L)));
	}

	@Test
	void execucaoFalhouPedeEstornoEAOsEsperaAConfirmacao() {
		SagaOrdemServico saga = saga(EtapaSaga.AGUARDANDO_EXECUCAO, SituacaoSaga.EM_ANDAMENTO);
		OrdemServico os = os(StatusOrdemServico.APROVADA);
		os.setPagamentoId(900L);
		os.setOrcamentoId(77L);

		orquestrador.execucaoFalhou(evento("ExecucaoFalhou", Map.of("motivo", "Sem estoque do item 20")));

		assertThat(os.getStatus()).as("só cancela depois do estorno").isEqualTo(StatusOrdemServico.APROVADA);
		assertThat(saga.getSituacao()).isEqualTo(SituacaoSaga.COMPENSANDO);
		assertThat(saga.getEtapa()).isEqualTo(EtapaSaga.AGUARDANDO_ESTORNO);
		assertThat(saga.getMotivo()).isEqualTo("Sem estoque do item 20");
		assertThat(saga.getPrazo()).isBetween(LocalDateTime.now().plusMinutes(9), LocalDateTime.now().plusMinutes(11));

		ArgumentCaptor<Object> dados = ArgumentCaptor.forClass(Object.class);
		verify(outboxService).registrar(eq(EventoSaida.ESTORNAR_PAGAMENTO), eq(OS_ID), eq("OS-2026-000042"), eq(SAGA_ID), dados.capture());
		EstornarPagamentoDados estorno = (EstornarPagamentoDados) dados.getValue();
		assertThat(estorno.pagamentoId()).isEqualTo(900L);
		assertThat(estorno.motivo()).isEqualTo("Sem estoque do item 20");
		assertThat(telemetria.eventos(SagaObservabilidade.EVENTO_TRANSICAO)).singleElement()
				.satisfies(evento -> assertThat(evento.atributos())
						.containsEntry("situacao", "COMPENSANDO")
						.containsEntry("motivo", "Sem estoque do item 20"));
	}

	@Test
	void faltaDePecaLevaAOsParaAguardandoItensEDepoisIniciaAExecucao() {
		SagaOrdemServico saga = saga(EtapaSaga.AGUARDANDO_EXECUCAO, SituacaoSaga.EM_ANDAMENTO);
		OrdemServico os = os(StatusOrdemServico.APROVADA);

		orquestrador.execucaoAguardandoItens(evento("ExecucaoAguardandoItens", Map.of()));
		assertThat(os.getStatus()).isEqualTo(StatusOrdemServico.AGUARDANDO_ITENS);
		assertThat(saga.getEtapa()).isEqualTo(EtapaSaga.AGUARDANDO_ITENS);
		assertThat(saga.getSituacao()).isEqualTo(SituacaoSaga.EM_ANDAMENTO);

		orquestrador.execucaoIniciada(evento("ExecucaoIniciada", Map.of()));
		assertThat(os.getStatus()).isEqualTo(StatusOrdemServico.EM_EXECUCAO);
		assertThat(saga.getEtapa()).isEqualTo(EtapaSaga.EM_EXECUCAO);
	}

	@Test
	void avisoRepetidoDeFaltaDePecaNaoMudaOStatusDeNovo() {
		SagaOrdemServico saga = saga(EtapaSaga.AGUARDANDO_ITENS, SituacaoSaga.EM_ANDAMENTO);
		OrdemServico os = os(StatusOrdemServico.AGUARDANDO_ITENS);

		orquestrador.execucaoAguardandoItens(evento("ExecucaoAguardandoItens", Map.of()));

		assertThat(os.getStatus()).isEqualTo(StatusOrdemServico.AGUARDANDO_ITENS);
		assertThat(saga.getEtapa()).isEqualTo(EtapaSaga.AGUARDANDO_ITENS);
		verify(historicoGateway, never()).save(any());
	}

	@Test
	void faltaDePecaDepoisDeIniciarAExecucaoEhRecusada() {
		saga(EtapaSaga.EM_EXECUCAO, SituacaoSaga.EM_ANDAMENTO);
		os(StatusOrdemServico.EM_EXECUCAO);

		assertThatThrownBy(() -> orquestrador.execucaoAguardandoItens(evento("ExecucaoAguardandoItens", Map.of())))
				.isInstanceOf(RegraNegocioException.class)
				.hasMessageContaining("EM_EXECUCAO");
	}

	@Test
	void servicoFinalizadoNaoMudaStatusNemEtapa() {
		SagaOrdemServico saga = saga(EtapaSaga.EM_EXECUCAO, SituacaoSaga.EM_ANDAMENTO);
		OrdemServico os = os(StatusOrdemServico.EM_EXECUCAO);

		orquestrador.servicoFinalizado(evento("ServicoFinalizado", Map.of("servicoId", 30, "duracaoMinutos", 45)));
		orquestrador.servicoFinalizado(evento("ServicoFinalizado", Map.of("servicoId", 31, "duracaoMinutos", 20)));

		assertThat(os.getStatus()).isEqualTo(StatusOrdemServico.EM_EXECUCAO);
		assertThat(saga.getEtapa()).isEqualTo(EtapaSaga.EM_EXECUCAO);
		assertThat(telemetria.eventos(SagaObservabilidade.EVENTO_TRANSICAO))
				.hasSize(2)
				.allSatisfy(e -> assertThat(e.atributos()).containsEntry("gatilho", "ServicoFinalizado"));
	}

	@Test
	void servicoFinalizadoForaDaExecucaoEhRecusado() {
		saga(EtapaSaga.AGUARDANDO_EXECUCAO, SituacaoSaga.EM_ANDAMENTO);
		os(StatusOrdemServico.APROVADA);

		assertThatThrownBy(() -> orquestrador.servicoFinalizado(evento("ServicoFinalizado", Map.of())))
				.isInstanceOf(RegraNegocioException.class);
	}

	@Test
	void execucaoFalhouNoMeioDoReparoPedeEstornoEACancelamentoVemDepois() {
		SagaOrdemServico saga = saga(EtapaSaga.EM_EXECUCAO, SituacaoSaga.EM_ANDAMENTO);
		OrdemServico os = os(StatusOrdemServico.EM_EXECUCAO);
		os.setPagamentoId(900L);

		orquestrador.execucaoFalhou(evento("ExecucaoFalhou",
				Map.of("motivo", "Peça danificada na montagem", "etapa", "EXECUCAO_SERVICO")));
		assertThat(saga.getEtapa()).isEqualTo(EtapaSaga.AGUARDANDO_ESTORNO);
		assertThat(saga.getSituacao()).isEqualTo(SituacaoSaga.COMPENSANDO);
		assertThat(os.getStatus()).isEqualTo(StatusOrdemServico.EM_EXECUCAO);

		orquestrador.pagamentoEstornado(evento("PagamentoEstornado", Map.of("pagamentoId", 900)));
		assertThat(os.getStatus()).as("o estorno cancela mesmo uma OS que já estava em execução")
				.isEqualTo(StatusOrdemServico.CANCELADA);
		assertThat(saga.getSituacao()).isEqualTo(SituacaoSaga.COMPENSADA);
	}

	@Test
	void execucaoFalhouEsperandoPecasPedeEstorno() {
		SagaOrdemServico saga = saga(EtapaSaga.AGUARDANDO_ITENS, SituacaoSaga.EM_ANDAMENTO);
		OrdemServico os = os(StatusOrdemServico.AGUARDANDO_ITENS);
		os.setPagamentoId(900L);

		orquestrador.execucaoFalhou(evento("ExecucaoFalhou", Map.of("motivo", "Peça descontinuada")));

		assertThat(saga.getEtapa()).isEqualTo(EtapaSaga.AGUARDANDO_ESTORNO);
		verify(outboxService).registrar(eq(EventoSaida.ESTORNAR_PAGAMENTO), eq(OS_ID), eq("OS-2026-000042"), eq(SAGA_ID), any());
	}

	@Test
	void execucaoFalhouDepoisDeFinalizadaEhRecusada() {
		saga(EtapaSaga.EM_EXECUCAO, SituacaoSaga.CONCLUIDA);
		os(StatusOrdemServico.FINALIZADA);

		assertThatThrownBy(() -> orquestrador.execucaoFalhou(evento("ExecucaoFalhou", Map.of())))
				.isInstanceOf(RegraNegocioException.class);
	}

	@Test
	void cancelarEnquantoACobrancaEstaEmAndamentoEhRecusado() {
		saga(EtapaSaga.AGUARDANDO_PAGAMENTO, SituacaoSaga.EM_ANDAMENTO);
		OrdemServico os = os(StatusOrdemServico.AGUARDANDO_APROVACAO);

		assertThatThrownBy(() -> orquestrador.cancelar(os))
				.isInstanceOf(RegraNegocioException.class)
				.hasMessageContaining("pagamento está sendo processado");
		assertThat(os.getStatus()).isEqualTo(StatusOrdemServico.AGUARDANDO_APROVACAO);
		verify(outboxService, never()).registrar(any(), any(), any(), any(), any());
	}

	@Test
	void cancelarOsPagaEsperandoPecasPedeEstorno() {
		SagaOrdemServico saga = saga(EtapaSaga.AGUARDANDO_ITENS, SituacaoSaga.EM_ANDAMENTO);
		OrdemServico os = os(StatusOrdemServico.AGUARDANDO_ITENS);
		os.setPagamentoId(900L);

		assertThat(orquestrador.cancelar(os)).isFalse();
		assertThat(saga.getEtapa()).isEqualTo(EtapaSaga.AGUARDANDO_ESTORNO);
		verify(outboxService).registrar(eq(EventoSaida.CANCELAR_EXECUCAO), eq(OS_ID), eq("OS-2026-000042"), eq(SAGA_ID), any());
	}

	@Test
	void pagamentoEstornadoCancelaAOsPagaECompensaASaga() {
		SagaOrdemServico saga = saga(EtapaSaga.AGUARDANDO_ESTORNO, SituacaoSaga.COMPENSANDO);
		OrdemServico os = os(StatusOrdemServico.APROVADA);
		os.setPagamentoId(900L);

		orquestrador.pagamentoEstornado(evento("PagamentoEstornado", Map.of("pagamentoId", 900)));

		assertThat(os.getStatus()).isEqualTo(StatusOrdemServico.CANCELADA);
		assertThat(saga.getSituacao()).isEqualTo(SituacaoSaga.COMPENSADA);
		assertHistorico(StatusOrdemServico.CANCELADA, "PagamentoEstornado");
	}

	@Test
	void eventoForaDeOrdemEhRecusadoSemMexerNaOs() {
		saga(EtapaSaga.AGUARDANDO_ORCAMENTO, SituacaoSaga.EM_ANDAMENTO);
		OrdemServico os = os(StatusOrdemServico.EM_DIAGNOSTICO);

		assertThatThrownBy(() -> orquestrador.pagamentoConfirmado(evento("PagamentoConfirmado", Map.of("pagamentoId", 1))))
				.isInstanceOf(RegraNegocioException.class)
				.hasMessageContaining("AGUARDANDO_ORCAMENTO");

		assertThat(os.getStatus()).isEqualTo(StatusOrdemServico.EM_DIAGNOSTICO);
		verify(ordemServicoGateway, never()).saveAndFlush(any());
	}

	/**
	 * Corrida real: o cliente cancelou a OS paga (estorno pendente) e a Execução, que ainda não
	 * tinha recebido o cancelamento, avisa que começou. O status da OS permitiria iniciar; a etapa da
	 * Saga é que impede executar um serviço que está sendo estornado.
	 */
	@Test
	void execucaoIniciadaDuranteOEstornoEhRecusada() {
		saga(EtapaSaga.AGUARDANDO_ESTORNO, SituacaoSaga.COMPENSANDO);
		OrdemServico os = os(StatusOrdemServico.APROVADA);
		os.setPagamentoId(900L);

		assertThatThrownBy(() -> orquestrador.execucaoIniciada(evento("ExecucaoIniciada", Map.of())))
				.isInstanceOf(RegraNegocioException.class)
				.hasMessageContaining("AGUARDANDO_ESTORNO");

		assertThat(os.getStatus()).isEqualTo(StatusOrdemServico.APROVADA);
	}

	/**
	 * A OS pediu orçamento de novo (Saga B) e o Billing responde, atrasado, o pedido da Saga A. A etapa
	 * da Saga B aceitaria o evento; só o sagaId mostra que ele é de outro ciclo.
	 */
	@Test
	void respostaDeUmaSagaAntigaEhRecusadaSemMexerNaOs() {
		SagaOrdemServico saga = saga(EtapaSaga.AGUARDANDO_ORCAMENTO, SituacaoSaga.EM_ANDAMENTO);
		OrdemServico os = os(StatusOrdemServico.EM_DIAGNOSTICO);
		UUID sagaAntiga = UUID.randomUUID();
		EnvelopeMensagem atrasado = new EnvelopeMensagem(UUID.randomUUID(), "OrcamentoGerado", sagaAntiga, OS_ID,
				"OS-2026-000042", "corr", Instant.now(), 1, jsonMapper.valueToTree(Map.of("orcamentoId", 77)));

		assertThatThrownBy(() -> orquestrador.orcamentoGerado(atrasado))
				.isInstanceOf(RegraNegocioException.class)
				.hasMessageContaining(sagaAntiga.toString())
				.hasMessageContaining(SAGA_ID.toString());

		assertThat(os.getStatus()).isEqualTo(StatusOrdemServico.EM_DIAGNOSTICO);
		assertThat(os.getOrcamentoId()).isNull();
		assertThat(saga.getEtapa()).isEqualTo(EtapaSaga.AGUARDANDO_ORCAMENTO);
		verify(ordemServicoGateway, never()).saveAndFlush(any());
	}

	@Test
	void eventoDepoisDoFimDaSagaEhRecusado() {
		saga(EtapaSaga.AGUARDANDO_APROVACAO, SituacaoSaga.CANCELADA);
		os(StatusOrdemServico.CANCELADA);

		assertThatThrownBy(() -> orquestrador.pagamentoConfirmado(evento("PagamentoConfirmado", Map.of("pagamentoId", 1))))
				.isInstanceOf(RegraNegocioException.class);
	}

	@Test
	void eventoDeOsSemSagaEhMensagemInvalida() {
		when(sagaRepository.travarPorOrdemServico(OS_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> orquestrador.execucaoIniciada(evento("ExecucaoIniciada", Map.of())))
				.isInstanceOf(MensagemInvalidaException.class)
				.hasMessageContaining("sem Saga");
	}

	@Test
	void eventoSemOrdemServicoIdEhMensagemInvalida() {
		EnvelopeMensagem semOs = new EnvelopeMensagem(UUID.randomUUID(), "ExecucaoIniciada", SAGA_ID, null, null, "c", Instant.now(), 1,
				jsonMapper.valueToTree(Map.of()));

		assertThatThrownBy(() -> orquestrador.execucaoIniciada(semOs)).isInstanceOf(MensagemInvalidaException.class);
	}

	@Test
	void eventoDeOsInexistenteEhMensagemInvalida() {
		saga(EtapaSaga.AGUARDANDO_EXECUCAO, SituacaoSaga.EM_ANDAMENTO);
		when(ordemServicoGateway.findById(OS_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> orquestrador.execucaoIniciada(evento("ExecucaoIniciada", Map.of())))
				.isInstanceOf(MensagemInvalidaException.class)
				.hasMessageContaining("não encontrada");
	}

	@Test
	void cancelarSemPagamentoCancelaNaHoraEEncerraASaga() {
		SagaOrdemServico saga = saga(EtapaSaga.AGUARDANDO_APROVACAO, SituacaoSaga.EM_ANDAMENTO);
		OrdemServico os = os(StatusOrdemServico.AGUARDANDO_APROVACAO);

		boolean cancelada = orquestrador.cancelar(os);

		assertThat(cancelada).isTrue();
		assertThat(os.getStatus()).isEqualTo(StatusOrdemServico.CANCELADA);
		assertThat(saga.getSituacao()).isEqualTo(SituacaoSaga.CANCELADA);
		assertThat(saga.getMotivo()).isEqualTo(OrquestradorSaga.MOTIVO_CANCELAMENTO_API);
		assertHistorico(StatusOrdemServico.CANCELADA, OrdemServicoStatusService.ORIGEM_API);
	}

	@Test
	void cancelarOsSemSagaCancelaNaHora() {
		when(sagaRepository.travarPorOrdemServico(OS_ID)).thenReturn(Optional.empty());
		OrdemServico os = os(StatusOrdemServico.RECEBIDA);

		assertThat(orquestrador.cancelar(os)).isTrue();
		assertThat(os.getStatus()).isEqualTo(StatusOrdemServico.CANCELADA);
	}

	@Test
	void cancelarComSagaEncerradaNaoMexeNaSaga() {
		SagaOrdemServico saga = saga(EtapaSaga.AGUARDANDO_ORCAMENTO, SituacaoSaga.COMPENSADA);
		saga.setMotivo("Billing não gerou o orçamento");
		OrdemServico os = os(StatusOrdemServico.EM_DIAGNOSTICO);

		assertThat(orquestrador.cancelar(os)).isTrue();
		assertThat(saga.getSituacao()).isEqualTo(SituacaoSaga.COMPENSADA);
		assertThat(saga.getMotivo()).isEqualTo("Billing não gerou o orçamento");
	}

	@Test
	void cancelarOsPagaAvisaAExecucaoPedeEstornoEEsperaAConfirmacao() {
		SagaOrdemServico saga = saga(EtapaSaga.AGUARDANDO_EXECUCAO, SituacaoSaga.EM_ANDAMENTO);
		OrdemServico os = os(StatusOrdemServico.APROVADA);
		os.setPagamentoId(900L);

		boolean cancelada = orquestrador.cancelar(os);

		assertThat(cancelada).isFalse();
		assertThat(os.getStatus()).isEqualTo(StatusOrdemServico.APROVADA);
		assertThat(saga.getSituacao()).isEqualTo(SituacaoSaga.COMPENSANDO);
		assertThat(saga.getEtapa()).isEqualTo(EtapaSaga.AGUARDANDO_ESTORNO);
		verify(outboxService).registrar(eq(EventoSaida.CANCELAR_EXECUCAO), eq(OS_ID), eq("OS-2026-000042"), eq(SAGA_ID), any());
		verify(outboxService).registrar(eq(EventoSaida.ESTORNAR_PAGAMENTO), eq(OS_ID), eq("OS-2026-000042"), eq(SAGA_ID), any());
		verify(historicoGateway, never()).save(any());
	}

	@Test
	void cancelarDuranteOEstornoEhRecusado() {
		saga(EtapaSaga.AGUARDANDO_ESTORNO, SituacaoSaga.COMPENSANDO);
		OrdemServico os = os(StatusOrdemServico.APROVADA);
		os.setPagamentoId(900L);

		assertThatThrownBy(() -> orquestrador.cancelar(os))
				.isInstanceOf(RegraNegocioException.class)
				.hasMessageContaining("já está em andamento");
		verify(outboxService, never()).registrar(any(), any(), any(), any(), any());
	}

	@Test
	void cancelarOsPagaComExecucaoIniciadaEhRecusado() {
		saga(EtapaSaga.EM_EXECUCAO, SituacaoSaga.EM_ANDAMENTO);
		OrdemServico os = os(StatusOrdemServico.EM_EXECUCAO);
		os.setPagamentoId(900L);

		assertThatThrownBy(() -> orquestrador.cancelar(os)).isInstanceOf(RegraNegocioException.class);
		verify(outboxService, never()).registrar(any(), any(), any(), any(), any());
	}

	@Test
	void cancelarOsPagaSemSagaExigeIntervencaoManual() {
		when(sagaRepository.travarPorOrdemServico(OS_ID)).thenReturn(Optional.empty());
		OrdemServico os = os(StatusOrdemServico.APROVADA);
		os.setPagamentoId(900L);

		assertThatThrownBy(() -> orquestrador.cancelar(os))
				.isInstanceOf(RegraNegocioException.class)
				.hasMessageContaining("intervenção manual");
	}

	private SagaOrdemServico saga(EtapaSaga etapa, SituacaoSaga situacao) {
		SagaOrdemServico saga = SagaOrdemServico.para(OS_ID);
		saga.setSagaId(SAGA_ID);
		saga.setEtapa(etapa);
		saga.setSituacao(situacao);
		when(sagaRepository.travarPorOrdemServico(OS_ID)).thenReturn(Optional.of(saga));
		return saga;
	}

	private OrdemServico os(StatusOrdemServico status) {
		OrdemServico os = new OrdemServico();
		os.setId(OS_ID);
		os.setCodigo("OS-2026-000042");
		os.setStatus(status);
		os.setDataCadastro(LocalDateTime.now().minusDays(1));
		Cliente cliente = new Cliente();
		cliente.setId(7L);
		cliente.setNome("Ana");
		Veiculo veiculo = new Veiculo();
		veiculo.setId(3L);
		veiculo.setPlaca("BRA2E19");
		os.setCliente(cliente);
		os.setVeiculo(veiculo);
		when(ordemServicoGateway.findById(OS_ID)).thenReturn(Optional.of(os));
		return os;
	}

	private EnvelopeMensagem evento(String tipo, Map<String, Object> dados) {
		return new EnvelopeMensagem(UUID.randomUUID(), tipo, SAGA_ID, OS_ID, "OS-2026-000042", "corr", Instant.now(), 1,
				jsonMapper.valueToTree(dados));
	}

	private void assertHistorico(StatusOrdemServico status, String origem) {
		ArgumentCaptor<HistoricoOrdemServico> historico = ArgumentCaptor.forClass(HistoricoOrdemServico.class);
		verify(historicoGateway).save(historico.capture());
		assertThat(historico.getValue().getStatus()).isEqualTo(status);
		assertThat(historico.getValue().getOrigem()).isEqualTo(origem);
	}

	private static ItemOrdemServico item() {
		ItemOrdemServico itemOs = new ItemOrdemServico();
		itemOs.setItemId(20L);
		itemOs.setItemNome("Filtro");
		itemOs.setQuantidade(2);
		return itemOs;
	}

	private static ServicoOrdemServico servico() {
		ServicoOrdemServico servicoOs = new ServicoOrdemServico();
		servicoOs.setServicoId(30L);
		servicoOs.setServicoNome("Troca de óleo");
		return servicoOs;
	}
}
