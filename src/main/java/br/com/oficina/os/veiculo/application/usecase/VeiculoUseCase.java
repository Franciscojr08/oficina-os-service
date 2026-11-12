package br.com.oficina.os.veiculo.application.usecase;

import br.com.oficina.os.veiculo.application.VeiculoService;
import br.com.oficina.os.veiculo.application.dto.VeiculoRequest;
import br.com.oficina.os.veiculo.application.dto.VeiculoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VeiculoUseCase {

	private final VeiculoService veiculoService;

	public VeiculoResponse criar(VeiculoRequest request) {
		return veiculoService.criar(request);
	}

	public List<VeiculoResponse> listar() {
		return veiculoService.listar();
	}

	public VeiculoResponse buscarPorId(Long id) {
		return veiculoService.buscarPorId(id);
	}

	public List<VeiculoResponse> listarPorCliente(Long clienteId) {
		return veiculoService.listarPorCliente(clienteId);
	}

	public VeiculoResponse atualizar(Long id, VeiculoRequest request) {
		return veiculoService.atualizar(id, request);
	}

	public VeiculoResponse buscarPorPlaca(String placa) {
		return veiculoService.buscarPorPlaca(placa);
	}

	public void inativar(Long id) {
		veiculoService.inativar(id);
	}
}
