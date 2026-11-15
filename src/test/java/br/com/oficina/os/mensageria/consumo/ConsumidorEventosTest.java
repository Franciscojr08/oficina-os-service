package br.com.oficina.os.mensageria.consumo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.oficina.os.mensageria.evento.EnvelopeMensagem;
import br.com.oficina.os.mensageria.porta.MensagemRecebida;
import br.com.oficina.os.mensageria.porta.MensagemRejeitadaException;
import br.com.oficina.os.shared.exception.RegraNegocioException;
import br.com.oficina.os.shared.observabilidade.CorrelationIdFilter;
import br.com.oficina.os.shared.observabilidade.TelemetriaEmMemoria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@ExtendWith(MockitoExtension.class)
class ConsumidorEventosTest {

	private static final UUID SAGA_ID = UUID.randomUUID();

	@Mock
	private ProcessadorEventos processador;

	private ConsumidorEventos consumidor;
	private final TelemetriaEmMemoria telemetria = new TelemetriaEmMemoria();

	@BeforeEach
	void setUp() {
		consumidor = new ConsumidorEventos(processador, JsonMapper.builder().build(), telemetria);
	}

	@Test
	void deveContinuarOTraceDeQuemEnviouENomearATransacao() {
		MensagemRecebida mensagem = new MensagemRecebida(
				corpoValido(),
				null,
				"OrcamentoGerado",
				"c",
				Map.of("traceparent", "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01",
						"newrelic", "eyJ2IjpbMCwxXX0="));

		consumidor.receber(mensagem);

		assertThat(telemetria.rastreamentosContinuados()).singleElement().satisfies(cabecalhos -> assertThat(cabecalhos)
				.containsEntry("traceparent", "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01")
				.containsEntry("newrelic", "eyJ2IjpbMCwxXX0=")
				.doesNotContainKey("x-correlation-id"));
		assertThat(telemetria.transacoes()).containsExactly("Mensagem/OrcamentoGerado");
	}

	@Test
	void mensagemSemTraceContinuaComCabecalhosVazios() {
		consumidor.receber(mensagemValida());

		assertThat(telemetria.rastreamentosContinuados()).singleElement().satisfies(c -> assertThat(c).isEmpty());
	}

	@Test
	void rejeicaoParaADlqViraEventoDeTelemetria() {
		when(processador.processar(any())).thenThrow(new RegraNegocioException("status não permite"));

		assertThatThrownBy(() -> consumidor.receber(mensagemValida()))
				.isInstanceOf(MensagemRejeitadaException.class);

		assertThat(telemetria.eventos(ConsumidorEventos.EVENTO_REJEITADA)).singleElement()
				.satisfies(evento -> assertThat(evento.atributos())
						.containsEntry("tipo", "OrcamentoGerado")
						.containsEntry("sagaId", SAGA_ID.toString())
						.containsEntry("motivo", "status não permite")
						.containsEntry("ordemServicoId", 1L));
	}

	@Test
	void rejeicaoDeJsonInvalidoViraEventoSemOrdemDeServico() {
		assertThatThrownBy(() -> consumidor.receber(mensagem("{ quebrado")))
				.isInstanceOf(MensagemRejeitadaException.class);

		assertThat(telemetria.eventos(ConsumidorEventos.EVENTO_REJEITADA)).singleElement()
				.satisfies(evento -> assertThat(evento.atributos())
						.doesNotContainKey("ordemServicoId")
						.doesNotContainKey("sagaId"));
	}

	@Test
	void deveProcessarOEnvelopeComOCorrelationIdNoMdc() {
		UUID messageId = UUID.randomUUID();
		AtomicReference<String> correlationDuranteProcessamento = new AtomicReference<>();
		doAnswer(invocacao -> {
			correlationDuranteProcessamento.set(MDC.get(CorrelationIdFilter.CHAVE_DE_LOG));
			return true;
		}).when(processador).processar(any());

		consumidor.receber(mensagem("""
				{"messageId":"%s","tipo":"OrcamentoGerado","sagaId":"%s","ordemServicoId":7,
				 "codigoOrdemServico":"OS-2026-000007","correlationId":"corr-env",
				 "ocorridoEm":"2026-11-06T10:00:00Z","versao":1,"dados":{"orcamentoId":3}}
				""".formatted(messageId, SAGA_ID)));

		ArgumentCaptor<EnvelopeMensagem> envelope = ArgumentCaptor.forClass(EnvelopeMensagem.class);
		verify(processador).processar(envelope.capture());
		assertThat(envelope.getValue().messageId()).isEqualTo(messageId);
		assertThat(envelope.getValue().sagaId()).isEqualTo(SAGA_ID);
		assertThat(envelope.getValue().ordemServicoId()).isEqualTo(7L);
		assertThat(envelope.getValue().codigoOrdemServico()).isEqualTo("OS-2026-000007");
		assertThat(envelope.getValue().dados().get("orcamentoId").asLong()).isEqualTo(3L);
		assertThat(correlationDuranteProcessamento.get()).isEqualTo("corr-env");
		assertThat(MDC.get(CorrelationIdFilter.CHAVE_DE_LOG)).isNull();
	}

	@Test
	void jsonInvalidoVaiDiretoParaADlq() {
		assertThatThrownBy(() -> consumidor.receber(mensagem("{ não é json")))
				.isInstanceOf(MensagemRejeitadaException.class)
				.hasMessageContaining("envelope JSON válido");

		verify(processador, never()).processar(any());
		assertThat(MDC.get(CorrelationIdFilter.CHAVE_DE_LOG)).isNull();
	}

	@Test
	void envelopeSemTipoVaiDiretoParaADlq() {
		assertThatThrownBy(() -> consumidor.receber(mensagem(
				"{\"sagaId\":\"%s\",\"messageId\":\"%s\"}".formatted(SAGA_ID, UUID.randomUUID()))))
				.isInstanceOf(MensagemRejeitadaException.class)
				.hasMessageContaining("sem tipo");
	}

	@Test
	void envelopeSemSagaIdVaiDiretoParaADlq() {
		assertThatThrownBy(() -> consumidor.receber(mensagem(
				"{\"tipo\":\"OrcamentoGerado\",\"messageId\":\"%s\"}".formatted(UUID.randomUUID()))))
				.isInstanceOf(MensagemRejeitadaException.class)
				.hasMessageContaining("sem sagaId");

		verify(processador, never()).processar(any());
	}

	@Test
	void sagaIdQueNaoEUmUuidVaiDiretoParaADlq() {
		assertThatThrownBy(() -> consumidor.receber(mensagem(
				"{\"tipo\":\"OrcamentoGerado\",\"sagaId\":\"nao-e-uuid\"}")))
				.isInstanceOf(MensagemRejeitadaException.class)
				.hasMessageContaining("envelope JSON válido");
	}

	@Test
	void envelopeSemMessageIdEhAceitoQuandoTemSagaId() {
		consumidor.receber(mensagem("{\"tipo\":\"OrcamentoGerado\",\"sagaId\":\"%s\",\"ordemServicoId\":1}".formatted(SAGA_ID)));

		ArgumentCaptor<EnvelopeMensagem> envelope = ArgumentCaptor.forClass(EnvelopeMensagem.class);
		verify(processador).processar(envelope.capture());
		assertThat(envelope.getValue().messageId()).isNull();
		assertThat(envelope.getValue().sagaId()).isEqualTo(SAGA_ID);
	}

	@Test
	void envelopeSemVersaoEhAceito() {
		consumidor.receber(mensagemValida());

		ArgumentCaptor<EnvelopeMensagem> envelope = ArgumentCaptor.forClass(EnvelopeMensagem.class);
		verify(processador).processar(envelope.capture());
		assertThat(envelope.getValue().versao()).isNull();
	}

	@Test
	void corpoNuloVaiDiretoParaADlq() {
		assertThatThrownBy(() -> consumidor.receber(mensagem("null")))
				.isInstanceOf(MensagemRejeitadaException.class);
	}

	@Test
	void eventoQueContradizOEstadoDaOsVaiDiretoParaADlq() {
		when(processador.processar(any())).thenThrow(new RegraNegocioException("status não permite"));

		assertThatThrownBy(() -> consumidor.receber(mensagemValida()))
				.isInstanceOf(MensagemRejeitadaException.class)
				.hasMessage("status não permite");
	}

	@Test
	void falhaTransitoriaSobeSemAlteracaoParaOContainerTentarDeNovo() {
		IllegalStateException banco = new IllegalStateException("banco fora");
		when(processador.processar(any())).thenThrow(banco);

		assertThatThrownBy(() -> consumidor.receber(mensagemValida())).isSameAs(banco);
	}

	@Test
	void semCorrelationIdNoCabecalhoNemNoEnvelopeGeraUmNovo() {
		AtomicReference<String> correlation = new AtomicReference<>();
		doAnswer(invocacao -> {
			correlation.set(MDC.get(CorrelationIdFilter.CHAVE_DE_LOG));
			return true;
		}).when(processador).processar(any());

		consumidor.receber(new MensagemRecebida(corpoValido(), null, null, null, Map.of()));

		assertThat(UUID.fromString(correlation.get())).isNotNull();
	}

	private static String corpoValido() {
		return "{\"messageId\":\"%s\",\"tipo\":\"OrcamentoGerado\",\"sagaId\":\"%s\",\"ordemServicoId\":1}"
				.formatted(UUID.randomUUID(), SAGA_ID);
	}

	private static MensagemRecebida mensagemValida() {
		return mensagem(corpoValido());
	}

	private static MensagemRecebida mensagem(String corpo) {
		return new MensagemRecebida(corpo, null, null, "corr-cabecalho", Map.of());
	}
}
