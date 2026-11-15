package br.com.oficina.os.ordemservico.application.dto;

import br.com.oficina.os.ordemservico.application.StatusOrdemServico;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Mudança de status da ordem de serviço")
public record HistoricoOrdemServicoResponse(
	@Schema(description = "Status assumido pela OS", example = "EM_DIAGNOSTICO")
	StatusOrdemServico status,
	@Schema(description = "Descrição do status", example = "Em diagnóstico")
	String descricao,
	@Schema(description = "Quem provocou a mudança: API para chamadas REST ou o nome do evento da Saga",
			example = "API")
	String origem,
	@Schema(description = "Data e hora da mudança", example = "2026-11-06T10:15:30")
	LocalDateTime data
) {
}
