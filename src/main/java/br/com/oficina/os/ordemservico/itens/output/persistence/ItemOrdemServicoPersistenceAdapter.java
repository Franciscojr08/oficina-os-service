package br.com.oficina.os.ordemservico.itens.output.persistence;

import br.com.oficina.os.ordemservico.itens.application.gateway.ItemOrdemServicoGateway;
import br.com.oficina.os.ordemservico.itens.domain.ItemOrdemServico;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ItemOrdemServicoPersistenceAdapter implements ItemOrdemServicoGateway {

	private final ItemOrdemServicoRepository itemOrdemServicoRepository;

	@Override
	public ItemOrdemServico save(ItemOrdemServico itemOrdemServico) {
		return itemOrdemServicoRepository.save(itemOrdemServico);
	}

	@Override
	public List<ItemOrdemServico> findByOrdemServicoId(Long ordemServicoId) {
		return itemOrdemServicoRepository.findByOrdemServicoId(ordemServicoId);
	}
}
