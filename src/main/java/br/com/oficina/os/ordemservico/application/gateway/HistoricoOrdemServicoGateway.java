package br.com.oficina.os.ordemservico.application.gateway;

import br.com.oficina.os.ordemservico.domain.HistoricoOrdemServico;

import java.util.List;

public interface HistoricoOrdemServicoGateway {

	HistoricoOrdemServico save(HistoricoOrdemServico historicoOrdemServico);

	/** Histórico da OS em ordem cronológica. */
	List<HistoricoOrdemServico> listarPorOrdemServico(Long ordemServicoId);
}
