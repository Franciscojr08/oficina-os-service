package br.com.oficina.os.saga.evento;

import br.com.oficina.os.ordemservico.domain.OrdemServico;

/** Compensação para o Billing: estornar o pagamento da OS. Formato do contrato: pagamentoId e motivo. */
public record EstornarPagamentoDados(Long pagamentoId, String motivo) {

	public static EstornarPagamentoDados de(OrdemServico ordemServico, String motivo) {
		return new EstornarPagamentoDados(ordemServico.getPagamentoId(), motivo);
	}
}
