package br.com.oficina.os.mensageria.consumo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.oficina.os.mensageria.evento.EnvelopeMensagem;
import br.com.oficina.os.shared.observabilidade.TelemetriaEmMemoria;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

class ProcessadorEventosTest {

	private final EventoProcessadoRepository repository = mock(EventoProcessadoRepository.class);
	private final ManipuladorEvento manipulador = manipulador("OrcamentoGerado");
	private final TelemetriaEmMemoria telemetria = new TelemetriaEmMemoria();

	@Test
	void deveRegistrarNoInboxEChamarOManipulador() {
		EnvelopeMensagem envelope = envelope("OrcamentoGerado");
		when(repository.registrar(envelope.messageId().toString(), envelope.messageId(), envelope.sagaId(), "OrcamentoGerado", 9L))
				.thenReturn(true);

		boolean processada = processador(manipulador).processar(envelope);

		assertThat(processada).isTrue();
		verify(manipulador).manipular(envelope);
		assertThat(telemetria.contador(ProcessadorEventos.CONTADOR_PROCESSADAS)).isEqualTo(1);
	}

	@Test
	void mensagemRepetidaDeveSerIgnorada() {
		EnvelopeMensagem envelope = envelope("OrcamentoGerado");
		when(repository.registrar(envelope.messageId().toString(), envelope.messageId(), envelope.sagaId(), "OrcamentoGerado", 9L))
				.thenReturn(false);

		boolean processada = processador(manipulador).processar(envelope);

		assertThat(processada).isFalse();
		verify(manipulador, never()).manipular(any());
		assertThat(telemetria.contador(ProcessadorEventos.CONTADOR_DUPLICADAS)).isEqualTo(1);
		assertThat(telemetria.contador(ProcessadorEventos.CONTADOR_PROCESSADAS)).isZero();
	}

	@Test
	void semMessageIdADeduplicacaoUsaSagaTipoEDados() {
		EnvelopeMensagem envelope = new EnvelopeMensagem(null, "OrcamentoGerado", UUID.randomUUID(), 9L, "OS-2026-000009",
				"corr", Instant.now(), 1, null);
		String chave = ChaveDeduplicacao.de(envelope);
		when(repository.registrar(chave, null, envelope.sagaId(), "OrcamentoGerado", 9L)).thenReturn(true);

		boolean processada = processador(manipulador).processar(envelope);

		assertThat(processada).isTrue();
		assertThat(chave).startsWith(envelope.sagaId() + ":");
		verify(manipulador).manipular(envelope);
	}

	@Test
	void tipoSemManipuladorEhMensagemInvalida() {
		EnvelopeMensagem envelope = envelope("EventoQueNinguemTrata");

		assertThatThrownBy(() -> processador(manipulador).processar(envelope))
				.isInstanceOf(MensagemInvalidaException.class)
				.hasMessageContaining("EventoQueNinguemTrata");

		verify(repository, never()).registrar(any(), any(), any(), any(), any());
	}

	@Test
	void doisManipuladoresParaOMesmoTipoImpedemASubida() {
		ManipuladorEvento outro = manipulador("OrcamentoGerado");

		assertThatThrownBy(() -> processador(manipulador, outro))
				.isInstanceOf(IllegalStateException.class);
	}

	@SuppressWarnings("unchecked")
	private ProcessadorEventos processador(ManipuladorEvento... manipuladores) {
		ObjectProvider<ManipuladorEvento> provider = mock(ObjectProvider.class);
		when(provider.orderedStream()).thenReturn(Stream.of(manipuladores));
		return new ProcessadorEventos(provider, repository, telemetria);
	}

	private static ManipuladorEvento manipulador(String tipo) {
		ManipuladorEvento manipulador = mock(ManipuladorEvento.class);
		when(manipulador.tipo()).thenReturn(tipo);
		return manipulador;
	}

	private static EnvelopeMensagem envelope(String tipo) {
		return new EnvelopeMensagem(UUID.randomUUID(), tipo, UUID.randomUUID(), 9L, "OS-2026-000009", "corr", Instant.now(), 1,
				null);
	}
}
