package br.com.oficina.os.mensageria.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import br.com.oficina.os.mensageria.MensageriaTestes;
import br.com.oficina.os.mensageria.consumo.EventoProcessadoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

@ExtendWith(MockitoExtension.class)
class LimpezaMensageriaTest {

	@Mock
	private OutboxEventoRepository outboxRepository;

	@Mock
	private EventoProcessadoRepository eventoProcessadoRepository;

	@Test
	void deveRemoverSoOQueEstaForaDaRetencao() {
		LocalDateTime antes = LocalDateTime.now();

		new LimpezaMensageria(outboxRepository, eventoProcessadoRepository, MensageriaTestes.propriedades()).limpar();

		ArgumentCaptor<LocalDateTime> limiteOutbox = ArgumentCaptor.forClass(LocalDateTime.class);
		ArgumentCaptor<LocalDateTime> limiteInbox = ArgumentCaptor.forClass(LocalDateTime.class);
		verify(outboxRepository).removerPublicadosAntesDe(limiteOutbox.capture());
		verify(eventoProcessadoRepository).removerProcessadosAntesDe(limiteInbox.capture());
		assertThat(limiteOutbox.getValue()).isBetween(antes.minusDays(7).minusSeconds(1), antes.minusDays(7).plusSeconds(5));
		assertThat(limiteInbox.getValue()).isBetween(antes.minusDays(30).minusSeconds(1), antes.minusDays(30).plusSeconds(5));
	}
}
