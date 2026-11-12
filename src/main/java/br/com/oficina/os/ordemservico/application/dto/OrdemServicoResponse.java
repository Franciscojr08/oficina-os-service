package br.com.oficina.os.ordemservico.application.dto;

import br.com.oficina.os.ordemservico.application.StatusOrdemServico;

import java.time.LocalDateTime;

public record OrdemServicoResponse(
        Long id,
        String codigo,
        String descricaoProblema,
        String observacoesGerais,
        String descricaoServicosExecutados,
        StatusOrdemServico status,
        LocalDateTime dataCadastro,
        LocalDateTime dataSolicitacaoOrcamento,
        LocalDateTime dataEnvioAprovacao,
        LocalDateTime dataAprovacao,
        LocalDateTime dataPagamento,
        LocalDateTime dataInicioExecucao,
        LocalDateTime dataFimExecucao,
        LocalDateTime dataEntregue,
        LocalDateTime dataCancelada,
        Long orcamentoId,
        Long pagamentoId
) {
}
