package br.com.oficina.os.saga.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.oficina.os.mensageria.outbox.OutboxService;
import br.com.oficina.os.ordemservico.application.HistoricoOrdemServicoService;
import br.com.oficina.os.ordemservico.application.OrdemServicoStatusService;
import br.com.oficina.os.ordemservico.application.StatusOrdemServico;
import br.com.oficina.os.ordemservico.application.gateway.HistoricoOrdemServicoGateway;
import br.com.oficina.os.ordemservico.application.gateway.OrdemServicoGateway;
import br.com.oficina.os.ordemservico.domain.HistoricoOrdemServico;
import br.com.oficina.os.ordemservico.domain.OrdemServico;
import br.com.oficina.os.saga.config.SagaProperties;
import br.com.oficina.os.saga.domain.EtapaSaga;
import br.com.oficina.os.saga.domain.SagaOrdemServico;
import br.com.oficina.os.saga.domain.SagaOrdemServicoRepository;
import br.com.oficina.os.saga.domain.SituacaoSaga;
import br.com.oficina.os.shared.observabilidade.OrdemServicoObservabilidade;
import br.com.oficina.os.shared.observabilidade.TelemetriaEmMemoria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VerificadorPrazosSagaTest {

	private static final long SAGA_ID = 5L;
	private static final long OS_ID = 42L;

	@Mock
	private SagaOrdemServicoRepository sagaRepository;

	@Mock
	private OrdemServicoGateway ordemServicoGateway;

	@Mock
	private HistoricoOrdemServicoGateway historicoGateway;

	@Mock
	private OutboxService outboxService;

	@Mock
	private TransactionTemplate transactionTemplate;

	private VerificadorPrazosSaga verificador;
	private final TelemetriaEmMemoria telemetria = new TelemetriaEmMemoria();

	@BeforeEach
	void setUp() {
		OrdemServicoStatusService statusService = new OrdemServicoStatusService(ordemServicoGateway,
				new HistoricoOrdemServicoService(historicoGateway), new OrdemServicoObservabilidade(new TelemetriaEmMemoria()), outboxService, sagaRepository);
		SagaProperties propriedades = new SagaProperties(Duration.ofMinutes(5), Duration.ofMinutes(10), 3,
				new SagaProperties.Verificacao(false, 1000, 50));
		verificador = new VerificadorPrazosSaga(sagaRepository, ordemServicoGateway, statusService, outboxService,
				transactionTemplate, propriedades, new SagaObservabilidade(telemetria));
		when(transactionTemplate.execute(any())).thenAnswer(invocacao ->
				invocacao.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
		when(sagaRepository.idsComPrazoVencido(any(), anyInt())).thenReturn(List.of(SAGA_ID));
		when(outboxService.reenviar(any())).thenReturn(true);
	}

	@Test
	void prazoVencidoComTentativasReenviaOMesmoComando() {
		SagaOrdemServico saga = vencida(EtapaSaga.AGUARDANDO_ORCAMENTO, 0);

		int tratadas = verificador.verificar();

		assertThat(tratadas).isEqualTo(1);
		verify(outboxService).reenviar(saga.getComandoMessageId());
		assertThat(saga.getTentativas()).isEqualTo(1);
		assertThat(saga.getPrazo()).isAfter(LocalDateTime.now().plusMinutes(4));
		assertThat(saga.getSituacao()).isEqualTo(SituacaoSaga.EM_ANDAMENTO);
		assertThat(telemetria.eventos(SagaObservabilidade.EVENTO_REENVIO)).singleElement()
				.satisfies(evento -> assertThat(evento.atributos()).containsEntry("tentativa", 2));
	}

	@Test
	void estornoUsaOPrazoDoEstornoNoReenvio() {
		SagaOrdemServico saga = vencida(EtapaSaga.AGUARDANDO_ESTORNO, 0);
		saga.setSituacao(SituacaoSaga.COMPENSANDO);

		verificador.verificar();

		assertThat(saga.getPrazo()).isAfter(LocalDateTime.now().plusMinutes(9));
	}

	@Test
	void comandoForaDoOutboxNaoImpedeOControleDeTentativas() {
		SagaOrdemServico saga = vencida(EtapaSaga.AGUARDANDO_ORCAMENTO, 0);
		when(outboxService.reenviar(any())).thenReturn(false);

		verificador.verificar();

		assertThat(saga.getTentativas()).isEqualTo(1);
	}

	@Test
	void orcamentoSemRespostaDepoisDasTentativasDevolveAOsAoDiagnostico() {
		SagaOrdemServico saga = vencida(EtapaSaga.AGUARDANDO_ORCAMENTO, 2);
		OrdemServico os = new OrdemServico();
		os.setId(OS_ID);
		os.setCodigo("OS-2026-000042");
		os.setStatus(StatusOrdemServico.EM_DIAGNOSTICO);
		when(ordemServicoGateway.findById(OS_ID)).thenReturn(Optional.of(os));

		verificador.verificar();

		verify(outboxService, never()).reenviar(any());
		assertThat(os.getStatus()).isEqualTo(StatusOrdemServico.EM_DIAGNOSTICO);
		assertThat(saga.getSituacao()).isEqualTo(SituacaoSaga.COMPENSADA);
		assertThat(saga.getPrazo()).isNull();
		verify(outboxService, never()).registrar(any(), any(), any(), any(), any());
		ArgumentCaptor<HistoricoOrdemServico> historico = ArgumentCaptor.forClass(HistoricoOrdemServico.class);
		verify(historicoGateway).save(historico.capture());
		assertThat(historico.getValue().getOrigem()).isEqualTo(VerificadorPrazosSaga.ORIGEM_PRAZO_ORCAMENTO);
	}

	@Test
	void estornoSemConfirmacaoDepoisDasTentativasPedeIntervencaoManual() {
		SagaOrdemServico saga = vencida(EtapaSaga.AGUARDANDO_ESTORNO, 2);
		saga.setSituacao(SituacaoSaga.COMPENSANDO);

		verificador.verificar();

		assertThat(saga.getSituacao()).isEqualTo(SituacaoSaga.FALHA_COMPENSACAO);
		assertThat(saga.getMotivo()).contains("estorno");
		assertThat(telemetria.eventos(SagaObservabilidade.EVENTO_TRANSICAO)).singleElement()
				.satisfies(evento -> assertThat(evento.atributos())
						.containsEntry("gatilho", "PrazoEstornoEsgotado")
						.containsEntry("situacao", "FALHA_COMPENSACAO"));
		verify(ordemServicoGateway, never()).saveAndFlush(any());
	}

	@Test
	void sagaJaTratadaPorOutraReplicaEhPulada() {
		when(sagaRepository.travarSeVencida(eq(SAGA_ID), any())).thenReturn(Optional.empty());

		assertThat(verificador.verificar()).isZero();
		verify(outboxService, never()).reenviar(any());
	}

	@Test
	void falhaEmUmaSagaNaoInterrompeAsOutras() {
		when(sagaRepository.idsComPrazoVencido(any(), anyInt())).thenReturn(List.of(1L, SAGA_ID));
		when(sagaRepository.travarSeVencida(eq(1L), any())).thenThrow(new IllegalStateException("banco"));
		SagaOrdemServico saga = vencida(EtapaSaga.AGUARDANDO_ORCAMENTO, 0);

		assertThat(verificador.verificar()).isEqualTo(1);
		assertThat(saga.getTentativas()).isEqualTo(1);
	}

	@Test
	void transacaoSemResultadoNaoContaComoTratada() {
		doReturn(null).when(transactionTemplate).execute(any());

		assertThat(verificador.verificar()).isZero();
	}

	private SagaOrdemServico vencida(EtapaSaga etapa, int tentativas) {
		SagaOrdemServico saga = SagaOrdemServico.para(OS_ID);
		saga.setId(SAGA_ID);
		saga.setEtapa(etapa);
		saga.setSituacao(SituacaoSaga.EM_ANDAMENTO);
		saga.setTentativas(tentativas);
		saga.setComandoMessageId(UUID.randomUUID());
		saga.setPrazo(LocalDateTime.now().minusMinutes(1));
		when(sagaRepository.travarSeVencida(eq(SAGA_ID), any())).thenReturn(Optional.of(saga));
		return saga;
	}
}
