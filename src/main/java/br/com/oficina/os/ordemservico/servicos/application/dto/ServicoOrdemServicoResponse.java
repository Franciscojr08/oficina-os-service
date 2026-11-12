package br.com.oficina.os.ordemservico.servicos.application.dto;

import java.time.LocalDateTime;

public record ServicoOrdemServicoResponse(
	String codigoOS,
	Long servicoId,
	String servicoNome,
	LocalDateTime dataCadastro
) {
}
