package br.com.oficina.os.ordemservico.servicos.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Serviço do catálogo do Billing: o id e o nome (que o OS guarda como cópia) vêm de quem chama a API. */
public record ServicoOrdemServicoRequest(
		@NotNull(message = "Id Servico é obrigatório")
		@Positive(message = "Id Servico deve ser maior que zero")
		Long servicoId,

		@NotBlank(message = "Nome do serviço é obrigatório")
		@Size(max = 120, message = "Nome do serviço deve ter no máximo 120 caracteres")
		String nome
) {
}
