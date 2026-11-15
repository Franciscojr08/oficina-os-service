package br.com.oficina.os.mensageria.evento;

import lombok.Getter;

/**
 * Eventos que o OS Service consome. O nome é a routing key na exchange do emissor e o campo
 * {@code tipo} do envelope. A reação a cada um (mudança de status e compensação) é do orquestrador
 * da Saga.
 */
@Getter
public enum EventoEntrada {
	ORCAMENTO_GERADO("OrcamentoGerado", Emissor.BILLING),
	PAGAMENTO_CONFIRMADO("PagamentoConfirmado", Emissor.BILLING),
	PAGAMENTO_RECUSADO("PagamentoRecusado", Emissor.BILLING),
	PAGAMENTO_ESTORNADO("PagamentoEstornado", Emissor.BILLING),
	EXECUCAO_AGUARDANDO_ITENS("ExecucaoAguardandoItens", Emissor.EXECUCAO),
	EXECUCAO_INICIADA("ExecucaoIniciada", Emissor.EXECUCAO),
	SERVICO_FINALIZADO("ServicoFinalizado", Emissor.EXECUCAO),
	EXECUCAO_FALHOU("ExecucaoFalhou", Emissor.EXECUCAO),
	EXECUCAO_FINALIZADA("ExecucaoFinalizada", Emissor.EXECUCAO);

	private final String tipo;
	private final Emissor emissor;

	EventoEntrada(String tipo, Emissor emissor) {
		this.tipo = tipo;
		this.emissor = emissor;
	}
}
