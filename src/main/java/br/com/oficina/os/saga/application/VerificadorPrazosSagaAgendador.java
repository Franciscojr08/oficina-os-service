package br.com.oficina.os.saga.application;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Dispara o verificador de prazos. Desligado nos testes, que verificam sob demanda. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "oficina.saga.verificacao.agendamento-habilitado", havingValue = "true",
		matchIfMissing = true)
public class VerificadorPrazosSagaAgendador {

	private final VerificadorPrazosSaga verificador;

	@Scheduled(fixedDelayString = "${oficina.saga.verificacao.intervalo-ms:30000}")
	public void verificar() {
		verificador.verificar();
	}
}
