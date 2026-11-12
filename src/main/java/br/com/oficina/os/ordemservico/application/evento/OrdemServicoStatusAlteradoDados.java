package br.com.oficina.os.ordemservico.application.evento;

import br.com.oficina.os.ordemservico.application.StatusOrdemServico;

import java.time.LocalDateTime;

/**
 * Dados do evento {@code OrdemServicoStatusAlterado}.
 *
 * @param statusAnterior nulo na abertura da OS
 * @param origem "API" ou o nome do evento que provocou a mudança
 */
public record OrdemServicoStatusAlteradoDados(
		Long ordemServicoId,
		String codigo,
		StatusOrdemServico statusAnterior,
		StatusOrdemServico statusAtual,
		String origem,
		LocalDateTime alteradoEm
) {
}
