package br.com.oficina.os.ordemservico.application.dto;

import br.com.oficina.os.ordemservico.application.StatusOrdemServico;

public record StatusOrdemServicoResponse(
	String codigo,
	StatusOrdemServico status
) {
}
