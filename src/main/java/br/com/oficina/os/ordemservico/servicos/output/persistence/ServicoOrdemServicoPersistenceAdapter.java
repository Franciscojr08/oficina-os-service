package br.com.oficina.os.ordemservico.servicos.output.persistence;

import br.com.oficina.os.ordemservico.servicos.application.gateway.ServicoOrdemServicoGateway;
import br.com.oficina.os.ordemservico.servicos.domain.ServicoOrdemServico;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ServicoOrdemServicoPersistenceAdapter implements ServicoOrdemServicoGateway {

	private final ServicoOrdemServicoRepository servicoOrdemServicoRepository;

	@Override
	public ServicoOrdemServico save(ServicoOrdemServico servicoOrdemServico) {
		return servicoOrdemServicoRepository.save(servicoOrdemServico);
	}

	@Override
	public List<ServicoOrdemServico> findByOrdemServicoId(Long ordemServicoId) {
		return servicoOrdemServicoRepository.findByOrdemServicoId(ordemServicoId);
	}
}
