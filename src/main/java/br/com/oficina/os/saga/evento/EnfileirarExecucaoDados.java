package br.com.oficina.os.saga.evento;

import br.com.oficina.os.ordemservico.itens.domain.ItemOrdemServico;
import br.com.oficina.os.ordemservico.servicos.domain.ServicoOrdemServico;

import java.util.List;

/**
 * Comando para a Execução: OS paga, reservar estoque e entrar na fila de reparo. Formato do contrato: o
 * serviço leva o nome (a Execução mostra "Troca de pastilhas" ao mecânico sem consultar o catálogo do
 * Billing) e o item leva só id e quantidade.
 */
public record EnfileirarExecucaoDados(List<ServicoDados> servicos, List<ItemDados> itens) {

	public record ServicoDados(Long servicoId, String nome) {
	}

	public record ItemDados(Long itemId, int quantidade) {
	}

	public static EnfileirarExecucaoDados de(List<ItemOrdemServico> itens, List<ServicoOrdemServico> servicos) {
		return new EnfileirarExecucaoDados(
				servicos.stream()
						.map(servico -> new ServicoDados(servico.getServicoId(), servico.getServicoNome()))
						.toList(),
				itens.stream()
						.map(item -> new ItemDados(item.getItemId(), item.getQuantidade()))
						.toList()
		);
	}
}
