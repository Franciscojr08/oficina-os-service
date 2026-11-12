package br.com.oficina.os.saga.domain;

public enum SituacaoSaga {
	EM_ANDAMENTO,
	/** Desfazendo um passo já confirmado (estorno do pagamento). */
	COMPENSANDO,
	/** OS finalizada. */
	CONCLUIDA,
	/** Encerrada sem nada a desfazer (orçamento reprovado, pagamento recusado, cancelamento antes do pagamento). */
	CANCELADA,
	/** Encerrada depois de compensar (orçamento falhou, estorno confirmado). */
	COMPENSADA,
	/** A compensação não se confirmou dentro das tentativas: precisa de intervenção manual. */
	FALHA_COMPENSACAO;

	public boolean ativa() {
		return this == EM_ANDAMENTO || this == COMPENSANDO;
	}
}
