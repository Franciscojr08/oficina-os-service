package br.com.oficina.os.ordemservico.itens.application.dto;

public record ItemOrdemServicoResponse(
	String codigoOS,
	Long itemId,
	String itemNome,
	int quantidade
) {
}
