package br.com.oficina.os.ordemservico.output.persistence;

import br.com.oficina.os.ordemservico.application.gateway.HistoricoOrdemServicoGateway;
import br.com.oficina.os.ordemservico.domain.HistoricoOrdemServico;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class HistoricoOrdemServicoPersistenceAdapter implements HistoricoOrdemServicoGateway {

	private final HistoricoOrdemServicoRepository historicoOrdemServicoRepository;

	@Override
	public HistoricoOrdemServico save(HistoricoOrdemServico historicoOrdemServico) {
		return historicoOrdemServicoRepository.save(historicoOrdemServico);
	}

	@Override
	public List<HistoricoOrdemServico> listarPorOrdemServico(Long ordemServicoId) {
		return historicoOrdemServicoRepository.findByOrdemServicoIdOrderByDataCadastroAscIdAsc(ordemServicoId);
	}
}
