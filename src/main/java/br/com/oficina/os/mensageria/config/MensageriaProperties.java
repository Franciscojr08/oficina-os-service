package br.com.oficina.os.mensageria.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nomes de exchanges e filas e parâmetros do outbox ({@code oficina.mensageria.*}).
 *
 * @param exchangeOs topic exchange onde o OS Service publica comandos e eventos
 * @param exchangeBilling topic exchange onde o Billing publica
 * @param exchangeExecucao topic exchange onde a Execução publica
 * @param filaEntrada fila do OS Service, ligada às exchanges do Billing e da Execução
 */
@ConfigurationProperties("oficina.mensageria")
public record MensageriaProperties(
		String exchangeOs,
		String exchangeBilling,
		String exchangeExecucao,
		String filaEntrada,
		Outbox outbox,
		Retencao retencao
) {

	/**
	 * @param agendamentoHabilitado liga o publicador agendado (desligado nos testes, que publicam sob demanda)
	 * @param intervaloMs intervalo entre rodadas do publicador
	 * @param lote máximo de eventos publicados por rodada
	 */
	public record Outbox(boolean agendamentoHabilitado, long intervaloMs, int lote) {
	}

	/**
	 * @param outboxDias por quantos dias um evento já publicado fica no outbox
	 * @param eventosProcessadosDias janela de deduplicação: por quantos dias o id de uma mensagem consumida é lembrado
	 */
	public record Retencao(int outboxDias, int eventosProcessadosDias) {
	}

	/** Exchange que recebe o que a fila de entrada rejeita. */
	public String exchangeDeadLetter() {
		return filaEntrada + "-dlx";
	}

	/** Fila de mensagens rejeitadas (dead-letter queue): {@code oficina-os-eventos-dlq}, como no contrato. */
	public String filaDeadLetter() {
		return filaEntrada + "-dlq";
	}
}
