package br.com.oficina.os.mensageria.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.oficina.os.mensageria.MensageriaTestes;
import br.com.oficina.os.mensageria.porta.EventoPublisherPort;
import br.com.oficina.os.mensageria.porta.FalhaPublicacaoException;
import br.com.oficina.os.mensageria.porta.MensagemSaida;
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
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OutboxPublicadorTest {

	@Mock
	private OutboxEventoRepository repository;

	@Mock
	private EventoPublisherPort publisher;

	@Mock
	private TransactionTemplate transactionTemplate;

	private OutboxPublicador publicador;
	private final TelemetriaEmMemoria telemetria = new TelemetriaEmMemoria();

	@BeforeEach
	void setUp() {
		publicador = new OutboxPublicador(repository, publisher, transactionTemplate, MensageriaTestes.propriedades(),
				JsonMapper.builder().build(), telemetria);
		when(transactionTemplate.execute(any())).thenAnswer(invocacao ->
				invocacao.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
	}

	@Test
	void devePublicarAMensagemEMarcarComoPublicadoDepoisDaConfirmacao() {
		OutboxEvento evento = evento(1L);
		when(repository.travarPendentes(50)).thenReturn(List.of(evento));

		int publicados = publicador.publicarPendentes();

		assertThat(publicados).isEqualTo(1);
		ArgumentCaptor<MensagemSaida> saida = ArgumentCaptor.forClass(MensagemSaida.class);
		verify(publisher).publicar(saida.capture());

		MensagemSaida mensagem = saida.getValue();
		assertThat(mensagem.messageId()).isEqualTo(evento.getMessageId());
		assertThat(mensagem.tipo()).isEqualTo("SolicitarOrcamento");
		assertThat(mensagem.destino()).isEqualTo("os.eventos");
		assertThat(mensagem.chaveRoteamento()).isEqualTo("SolicitarOrcamento");
		assertThat(mensagem.ordemServicoId()).isEqualTo(10L);
		assertThat(mensagem.correlationId()).isEqualTo("corr-1");
		assertThat(mensagem.corpo()).isEqualTo("{\"tipo\":\"x\"}");
		assertThat(mensagem.criadoEm()).isEqualTo(evento.getCriadoEm());

		assertThat(evento.getPublicadoEm()).isNotNull();
		assertThat(evento.getUltimoErro()).isNull();
	}

	@Test
	void deveLevarOTraceDeQuemGerouOEventoNosCabecalhosDaMensagem() {
		OutboxEvento evento = evento(1L);
		evento.setRastreamento("{\"traceparent\":\"00-abc-def-01\",\"newrelic\":\"eyJ2Ijp9\"}");
		when(repository.travarPendentes(50)).thenReturn(List.of(evento));

		publicador.publicarPendentes();

		ArgumentCaptor<MensagemSaida> mensagem = ArgumentCaptor.forClass(MensagemSaida.class);
		verify(publisher).publicar(mensagem.capture());
		assertThat(mensagem.getValue().rastreamento())
				.containsEntry("traceparent", "00-abc-def-01")
				.containsEntry("newrelic", "eyJ2Ijp9");
	}

	@Test
	void rastreamentoCorrompidoNaoImpedeAPublicacao() {
		OutboxEvento evento = evento(1L);
		evento.setRastreamento("{ corrompido");
		when(repository.travarPendentes(50)).thenReturn(List.of(evento));

		assertThat(publicador.publicarPendentes()).isEqualTo(1);
		assertThat(evento.getPublicadoEm()).isNotNull();
		ArgumentCaptor<MensagemSaida> mensagem = ArgumentCaptor.forClass(MensagemSaida.class);
		verify(publisher).publicar(mensagem.capture());
		assertThat(mensagem.getValue().rastreamento()).isEmpty();
	}

	@Test
	void deveRegistrarQuantosEventosContinuamPendentes() {
		when(repository.travarPendentes(50)).thenReturn(List.of());
		when(repository.countByPublicadoEmIsNull()).thenReturn(7L);

		publicador.publicarPendentes();

		assertThat(telemetria.metrica(OutboxPublicador.METRICA_PENDENTES)).isEqualTo(7.0);
	}

	@Test
	void falhaNoBrokerDeveManterPendenteRegistrarTentativaEPararARodada() {
		OutboxEvento primeiro = evento(1L);
		OutboxEvento segundo = evento(2L);
		OutboxEvento terceiro = evento(3L);
		when(repository.travarPendentes(50)).thenReturn(List.of(primeiro, segundo, terceiro));
		doThrow(new FalhaPublicacaoException("broker fora", new RuntimeException("broker fora")))
				.doNothing()
				.when(publisher).publicar(any());

		int publicados = publicador.publicarPendentes();

		assertThat(publicados).isZero();
		assertThat(primeiro.getPublicadoEm()).isNull();
		assertThat(primeiro.getTentativas()).isEqualTo(1);
		assertThat(primeiro.getUltimoErro()).contains("broker fora");
		// A rodada para no primeiro erro, para não inverter a ordem dos eventos
		assertThat(segundo.getTentativas()).isZero();
		assertThat(terceiro.getPublicadoEm()).isNull();
		verify(publisher, times(1)).publicar(any());
		assertThat(telemetria.eventos(OutboxPublicador.EVENTO_FALHA)).singleElement()
				.satisfies(falha -> assertThat(falha.atributos()).containsEntry("tentativas", 1));
	}

	@Test
	void erroLongoDeveSerTruncado() {
		OutboxEvento evento = evento(1L);
		when(repository.travarPendentes(50)).thenReturn(List.of(evento));
		doThrow(new FalhaPublicacaoException("x".repeat(2_000), new RuntimeException()))
				.when(publisher).publicar(any());

		publicador.publicarPendentes();

		assertThat(evento.getUltimoErro()).hasSize(500);
	}

	@Test
	void semPendentesNaoPublicaNada() {
		when(repository.travarPendentes(50)).thenReturn(List.of());

		assertThat(publicador.publicarPendentes()).isZero();
		verify(publisher, never()).publicar(any());
	}

	@Test
	void transacaoSemResultadoContaComoZero() {
		doReturn(null).when(transactionTemplate).execute(any());

		assertThat(publicador.publicarPendentes()).isZero();
	}

	private static OutboxEvento evento(Long id) {
		OutboxEvento evento = new OutboxEvento();
		evento.setId(id);
		evento.setMessageId(UUID.randomUUID());
		evento.setTipo("SolicitarOrcamento");
		evento.setExchange("os.eventos");
		evento.setRoutingKey("SolicitarOrcamento");
		evento.setOrdemServicoId(10L);
		evento.setCorrelationId("corr-1");
		evento.setPayload("{\"tipo\":\"x\"}");
		evento.setCriadoEm(LocalDateTime.now());
		return evento;
	}
}
