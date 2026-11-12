package br.com.oficina.os.mensageria.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import br.com.oficina.os.mensageria.MensageriaTestes;
import br.com.oficina.os.mensageria.evento.EnvelopeMensagem;
import br.com.oficina.os.mensageria.evento.EventoSaida;
import br.com.oficina.os.shared.observabilidade.CorrelationIdFilter;
import br.com.oficina.os.shared.observabilidade.TelemetriaEmMemoria;
import org.junit.jupiter.api.AfterEach;
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

@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {

	@Mock
	private OutboxEventoRepository repository;

	private static final UUID SAGA_ID = UUID.randomUUID();

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	private OutboxService outboxService;
	private final TelemetriaEmMemoria telemetria = new TelemetriaEmMemoria();

	@BeforeEach
	void setUp() {
		outboxService = new OutboxService(repository, jsonMapper, MensageriaTestes.propriedades(), telemetria);
	}

	@Test
	void deveGuardarOContextoDoTraceAtualJuntoComOEvento() {
		telemetria.traceAtual(Map.of(
				"traceparent", "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01",
				"newrelic", "eyJ2IjpbMCwxXX0="));

		outboxService.registrar(EventoSaida.SOLICITAR_ORCAMENTO, 42L, "OS-2026-000042", SAGA_ID, Map.of());

		Map<String, String> guardado = jsonMapper.readValue(capturarSalvo().getRastreamento(),
				new tools.jackson.core.type.TypeReference<Map<String, String>>() { });
		assertThat(guardado)
				.containsEntry("traceparent", "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01")
				.containsEntry("newrelic", "eyJ2IjpbMCwxXX0=");
	}

	@Test
	void semTraceAtivoNaoGuardaRastreamento() {
		outboxService.registrar(EventoSaida.SOLICITAR_ORCAMENTO, 42L, "OS-2026-000042", SAGA_ID, Map.of());

		assertThat(capturarSalvo().getRastreamento()).isNull();
	}

	@AfterEach
	void limparMdc() {
		MDC.clear();
	}

	@Test
	void deveGravarEnvelopeComOCorrelationIdDaRequisicao() {
		MDC.put(CorrelationIdFilter.CHAVE_DE_LOG, "req-123");

		UUID messageId = outboxService.registrar(EventoSaida.SOLICITAR_ORCAMENTO, 42L, "OS-2026-000042", SAGA_ID,
				Map.of("codigo", "OS-2026-000042"));

		OutboxEvento salvo = capturarSalvo();
		assertThat(salvo.getMessageId()).isEqualTo(messageId);
		assertThat(salvo.getTipo()).isEqualTo("SolicitarOrcamento");
		assertThat(salvo.getExchange()).isEqualTo("os.eventos");
		assertThat(salvo.getRoutingKey()).isEqualTo("SolicitarOrcamento");
		assertThat(salvo.getOrdemServicoId()).isEqualTo(42L);
		assertThat(salvo.getSagaId()).isEqualTo(SAGA_ID);
		assertThat(salvo.getCorrelationId()).isEqualTo("req-123");
		assertThat(salvo.getPublicadoEm()).isNull();

		EnvelopeMensagem envelope = jsonMapper.readValue(salvo.getPayload(), EnvelopeMensagem.class);
		assertThat(envelope.messageId()).isEqualTo(messageId);
		assertThat(envelope.tipo()).isEqualTo("SolicitarOrcamento");
		assertThat(envelope.sagaId()).isEqualTo(SAGA_ID);
		assertThat(envelope.ordemServicoId()).isEqualTo(42L);
		assertThat(envelope.codigoOrdemServico()).isEqualTo("OS-2026-000042");
		assertThat(envelope.correlationId()).isEqualTo("req-123");
		assertThat(envelope.versao()).isEqualTo(EnvelopeMensagem.VERSAO_ATUAL);
		assertThat(envelope.ocorridoEm()).isNotNull();
		assertThat(envelope.dados().get("codigo").asString()).isEqualTo("OS-2026-000042");
	}

	@Test
	void eventoInformativoForaDeUmaSagaSaiSemSagaId() {
		outboxService.registrar(EventoSaida.ORDEM_SERVICO_STATUS_ALTERADO, 1L, "OS-2026-000001", null, Map.of());

		OutboxEvento salvo = capturarSalvo();
		assertThat(salvo.getSagaId()).isNull();
		assertThat(jsonMapper.readValue(salvo.getPayload(), EnvelopeMensagem.class).sagaId()).isNull();
	}

	@Test
	void foraDeUmaRequisicaoDeveGerarCorrelationIdNovo() {
		outboxService.registrar(EventoSaida.ORDEM_SERVICO_STATUS_ALTERADO, 1L, "OS-2026-000001", null, Map.of());

		assertThat(UUID.fromString(capturarSalvo().getCorrelationId())).isNotNull();
	}

	@Test
	void cadaEventoDeveTerMessageIdProprio() {
		UUID primeiro = outboxService.registrar(EventoSaida.ORDEM_SERVICO_STATUS_ALTERADO, 1L, "OS-2026-000001", null, Map.of());
		UUID segundo = outboxService.registrar(EventoSaida.ORDEM_SERVICO_STATUS_ALTERADO, 1L, "OS-2026-000001", null, Map.of());

		assertThat(primeiro).isNotEqualTo(segundo);
	}

	private OutboxEvento capturarSalvo() {
		ArgumentCaptor<OutboxEvento> captor = ArgumentCaptor.forClass(OutboxEvento.class);
		verify(repository).save(captor.capture());
		return captor.getValue();
	}
}
