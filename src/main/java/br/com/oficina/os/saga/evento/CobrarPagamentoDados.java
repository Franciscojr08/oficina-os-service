package br.com.oficina.os.saga.evento;

import br.com.oficina.os.ordemservico.domain.OrdemServico;

/** Comando para o Billing: cobrar o orçamento aprovado pelo cliente. Formato do contrato: só o orcamentoId. */
public record CobrarPagamentoDados(Long orcamentoId) {

	public static CobrarPagamentoDados de(OrdemServico ordemServico) {
		return new CobrarPagamentoDados(ordemServico.getOrcamentoId());
	}
}
