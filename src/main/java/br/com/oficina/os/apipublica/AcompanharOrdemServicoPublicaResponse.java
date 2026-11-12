package br.com.oficina.os.apipublica;

import br.com.oficina.os.ordemservico.application.StatusOrdemServico;

import java.time.LocalDateTime;

public record AcompanharOrdemServicoPublicaResponse(
        String codigo,
        String descricaoProblema,
        StatusOrdemServico status,
        LocalDateTime dataCadastro,
        LocalDateTime dataInicioExecucao,
        LocalDateTime dataFimExecucao,
        LocalDateTime dataEntregue,
        LocalDateTime dataCancelada
) {
}
