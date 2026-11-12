package br.com.oficina.os.ordemservico.itens.application.dto;

import java.util.List;

public record ListaItensOrdemServicoResponse(
	List<ItemOrdemServicoResponse> itens
) {
}
