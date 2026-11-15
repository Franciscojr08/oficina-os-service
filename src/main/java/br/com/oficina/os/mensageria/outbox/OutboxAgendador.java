package br.com.oficina.os.mensageria.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dispara o publicador do outbox em intervalo fixo. Nos testes de integração fica desligado
 * ({@code oficina.mensageria.outbox.agendamento-habilitado=false}) e a publicação é chamada sob
 * demanda, para o teste controlar o momento.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "oficina.mensageria.outbox.agendamento-habilitado", havingValue = "true",
		matchIfMissing = true)
public class OutboxAgendador {

	private final OutboxPublicador publicador;

	@Scheduled(fixedDelayString = "${oficina.mensageria.outbox.intervalo-ms:1000}")
	public void publicar() {
		publicador.publicarPendentes();
	}
}
