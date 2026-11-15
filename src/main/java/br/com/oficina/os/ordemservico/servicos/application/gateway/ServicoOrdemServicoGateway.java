package br.com.oficina.os.ordemservico.servicos.application.gateway;

import br.com.oficina.os.ordemservico.servicos.domain.ServicoOrdemServico;

import java.util.List;

public interface ServicoOrdemServicoGateway {

	ServicoOrdemServico save(ServicoOrdemServico servicoOrdemServico);

	List<ServicoOrdemServico> findByOrdemServicoId(Long ordemServicoId);
}
