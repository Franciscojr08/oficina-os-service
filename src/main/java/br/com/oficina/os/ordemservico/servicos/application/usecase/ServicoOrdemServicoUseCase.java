package br.com.oficina.os.ordemservico.servicos.application.usecase;

import br.com.oficina.os.ordemservico.servicos.application.ServicoOrdemServicoService;
import br.com.oficina.os.ordemservico.servicos.application.dto.ListaServicosOrdemServicoResponse;
import br.com.oficina.os.ordemservico.servicos.application.dto.ServicoOrdemServicoRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ServicoOrdemServicoUseCase {

	private final ServicoOrdemServicoService servicoOrdemServicoService;

	public ListaServicosOrdemServicoResponse adicionarServico(Long ordemServicoId, ServicoOrdemServicoRequest request) {
		servicoOrdemServicoService.adicionarServico(ordemServicoId, request);
		return servicoOrdemServicoService.listarServicosPorOrdemServico(ordemServicoId);
	}

	public ListaServicosOrdemServicoResponse listarServicosPorOrdemServico(Long ordemServicoId) {
		return servicoOrdemServicoService.listarServicosPorOrdemServico(ordemServicoId);
	}
}
