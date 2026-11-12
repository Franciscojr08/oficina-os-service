package br.com.oficina.os.saga.domain;

/** Ponto do fluxo distribuído em que a OS está, esperando a resposta de outro serviço (ou do cliente). */
public enum EtapaSaga {
	/** SolicitarOrcamento enviado; espera OrcamentoGerado do Billing. */
	AGUARDANDO_ORCAMENTO,
	/** Orçamento com o cliente; espera a aprovação ou a reprovação pela API da OS. */
	AGUARDANDO_APROVACAO,
	/** CobrarPagamento enviado; espera PagamentoConfirmado ou PagamentoRecusado. */
	AGUARDANDO_PAGAMENTO,
	/** EnfileirarExecucao enviada; espera ExecucaoIniciada, ExecucaoAguardandoItens ou ExecucaoFalhou. */
	AGUARDANDO_EXECUCAO,
	/** Falta peça no estoque; espera ExecucaoIniciada (quando chegar) ou ExecucaoFalhou. */
	AGUARDANDO_ITENS,
	/** Em execução; recebe ServicoFinalizado por serviço e espera ExecucaoFinalizada ou ExecucaoFalhou. */
	EM_EXECUCAO,
	/** Compensação: EstornarPagamento enviado; espera PagamentoEstornado. */
	AGUARDANDO_ESTORNO
}
