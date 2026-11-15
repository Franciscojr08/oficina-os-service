package br.com.oficina.os.ordemservico.itens.application.gateway;

import br.com.oficina.os.ordemservico.itens.domain.ItemOrdemServico;

import java.util.List;

public interface ItemOrdemServicoGateway {

	ItemOrdemServico save(ItemOrdemServico itemOrdemServico);

	List<ItemOrdemServico> findByOrdemServicoId(Long ordemServicoId);
}
