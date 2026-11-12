package br.com.oficina.os.ordemservico.servicos.application.dto;

import java.util.List;

public record ListaServicosOrdemServicoResponse(
	List<ServicoOrdemServicoResponse> servicos
) {
}
