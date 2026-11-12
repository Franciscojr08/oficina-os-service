package br.com.oficina.os.ordemservico.application.usecase;

import br.com.oficina.os.ordemservico.application.OrdemServicoService;
import br.com.oficina.os.ordemservico.application.StatusOrdemServico;
import br.com.oficina.os.ordemservico.application.dto.HistoricoOrdemServicoResponse;
import br.com.oficina.os.ordemservico.application.dto.OrdemServicoRequest;
import br.com.oficina.os.ordemservico.application.dto.OrdemServicoResponse;
import br.com.oficina.os.ordemservico.application.dto.StatusOrdemServicoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrdemServicoUseCase {

	private final OrdemServicoService ordemServicoService;

	public List<OrdemServicoResponse> listar() {
		return ordemServicoService.listar();
	}

	public OrdemServicoResponse buscarPorId(Long id) {
		return ordemServicoService.buscarPorId(id);
	}

	public List<OrdemServicoResponse> listarPorCliente(Long clienteId) {
		return ordemServicoService.listarPorCliente(clienteId);
	}

	public List<OrdemServicoResponse> listarPorCodigo(String codigo) {
		return ordemServicoService.listarPorCodigo(codigo);
	}

	public List<OrdemServicoResponse> listarPorStatus(StatusOrdemServico status) {
		return ordemServicoService.listarPorStatus(status);
	}

	public OrdemServicoResponse criar(OrdemServicoRequest request) {
		return ordemServicoService.criar(request);
	}

	public OrdemServicoResponse atualizar(Long id, OrdemServicoRequest request) {
		return ordemServicoService.atualizar(id, request);
	}

	public OrdemServicoResponse iniciarDiagnostico(Long id) {
		return ordemServicoService.iniciarDiagnostico(id);
	}

	public OrdemServicoResponse solicitarAprovacao(Long id) {
		return ordemServicoService.solicitarAprovacao(id);
	}

	public OrdemServicoResponse aprovar(Long id) {
		return ordemServicoService.aprovar(id);
	}

	public OrdemServicoResponse reprovar(Long id) {
		return ordemServicoService.reprovar(id);
	}

	public OrdemServicoResponse entregar(Long id) {
		return ordemServicoService.entregarOrdemServico(id);
	}

	public OrdemServicoResponse cancelar(Long id) {
		return ordemServicoService.cancelar(id);
	}

	public List<HistoricoOrdemServicoResponse> consultarHistorico(Long id) {
		return ordemServicoService.consultarHistorico(id);
	}

	public StatusOrdemServicoResponse consultarStatus(Long id) {
		return ordemServicoService.consultarStatus(id);
	}
}
