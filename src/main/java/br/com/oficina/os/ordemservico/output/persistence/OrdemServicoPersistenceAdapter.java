package br.com.oficina.os.ordemservico.output.persistence;

import br.com.oficina.os.ordemservico.application.StatusOrdemServico;
import br.com.oficina.os.ordemservico.application.gateway.OrdemServicoGateway;
import br.com.oficina.os.ordemservico.domain.OrdemServico;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class OrdemServicoPersistenceAdapter implements OrdemServicoGateway {

	private final OrdemServicoRepository ordemServicoRepository;

	@Override
	public OrdemServico save(OrdemServico ordemServico) {
		return ordemServicoRepository.save(ordemServico);
	}

	@Override
	public OrdemServico saveAndFlush(OrdemServico ordemServico) {
		return ordemServicoRepository.saveAndFlush(ordemServico);
	}

	@Override
	public List<OrdemServico> findByClienteId(Long clienteId) {
		return ordemServicoRepository.findByClienteId(clienteId);
	}

	@Override
	public Optional<OrdemServico> findByCodigo(String codigo) {
		return ordemServicoRepository.findByCodigo(codigo);
	}

	@Override
	public List<OrdemServico> findByStatus(StatusOrdemServico status) {
		return ordemServicoRepository.findByStatus(status);
	}

	@Override
	public boolean existsByClienteIdAndStatusIn(Long clienteId, List<StatusOrdemServico> statusOrdemServicos) {
		return ordemServicoRepository.existsByClienteIdAndStatusIn(clienteId, statusOrdemServicos);
	}

	@Override
	public boolean existsByVeiculoIdAndStatusIn(Long veiculoId, List<StatusOrdemServico> statusOrdemServicos) {
		return ordemServicoRepository.existsByVeiculoIdAndStatusIn(veiculoId, statusOrdemServicos);
	}


	@Override
	public List<OrdemServico> listagemOrdensServico() {
		return ordemServicoRepository.listagemOrdensServico();
	}

	@Override
	public Optional<OrdemServico> findById(Long id) {
		return ordemServicoRepository.findById(id);
	}
}
