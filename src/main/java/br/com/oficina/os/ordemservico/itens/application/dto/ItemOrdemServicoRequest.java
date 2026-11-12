package br.com.oficina.os.ordemservico.itens.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Item do catálogo do Billing: o id e o nome (que o OS guarda como cópia) vêm de quem chama a API. */
public record ItemOrdemServicoRequest(
	@NotNull(message = "Id Item é obrigatório")
	@Positive(message = "Id Item deve ser maior que zero")
	Long itemId,

	@NotBlank(message = "Nome do item é obrigatório")
	@Size(max = 120, message = "Nome do item deve ter no máximo 120 caracteres")
	String nome,

	@NotNull(message = "Quantidade é obrigatório")
	@Positive(message = "Quantidade deve ser maior que zero")
	Integer quantidade
) {
}
