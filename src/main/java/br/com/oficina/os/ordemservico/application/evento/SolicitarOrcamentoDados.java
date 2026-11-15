package br.com.oficina.os.ordemservico.application.evento;

import br.com.oficina.os.ordemservico.domain.OrdemServico;
import br.com.oficina.os.ordemservico.itens.domain.ItemOrdemServico;
import br.com.oficina.os.ordemservico.servicos.domain.ServicoOrdemServico;

import java.util.List;

/**
 * Dados do comando {@code SolicitarOrcamento}, como no contrato da Fase 4: só ids e quantidades. O OS
 * Service não guarda preço nenhum; o Billing é dono do catálogo e monta o orçamento com os preços dele.
 * Nome, CPF e e-mail do cliente também não vão: o Billing só precisa do id.
 */
public record SolicitarOrcamentoDados(
		Long clienteId,
		Long veiculoId,
		List<ServicoDados> servicos,
		List<ItemDados> itens
) {

	public record ServicoDados(Long servicoId) {
	}

	public record ItemDados(Long itemId, int quantidade) {
	}

	public static SolicitarOrcamentoDados de(
			OrdemServico ordemServico,
			List<ItemOrdemServico> itens,
			List<ServicoOrdemServico> servicos
	) {
		return new SolicitarOrcamentoDados(
				ordemServico.getCliente().getId(),
				ordemServico.getVeiculo().getId(),
				servicos.stream().map(servico -> new ServicoDados(servico.getServicoId())).toList(),
				itens.stream().map(item -> new ItemDados(item.getItemId(), item.getQuantidade())).toList()
		);
	}
}
