package br.com.oficina.os.saga.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Prazos da Saga ({@code oficina.saga.*}). Só as etapas que esperam resposta automática de outro
 * serviço têm prazo; aprovação, pagamento e execução dependem de pessoas e não expiram.
 *
 * @param prazoOrcamento tempo para o Billing responder ao SolicitarOrcamento
 * @param prazoEstorno tempo para o Billing confirmar o estorno
 * @param maxTentativas envios do comando (o original e os reenvios) antes de compensar
 */
@ConfigurationProperties("oficina.saga")
public record SagaProperties(
		Duration prazoOrcamento,
		Duration prazoEstorno,
		int maxTentativas,
		Verificacao verificacao
) {

	/**
	 * @param agendamentoHabilitado liga o verificador agendado (desligado nos testes, que verificam sob demanda)
	 * @param intervaloMs intervalo entre verificações
	 * @param lote máximo de Sagas tratadas por verificação
	 */
	public record Verificacao(boolean agendamentoHabilitado, long intervaloMs, int lote) {
	}
}
