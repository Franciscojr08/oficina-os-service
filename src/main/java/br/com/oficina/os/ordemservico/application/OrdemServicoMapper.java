package br.com.oficina.os.ordemservico.application;

import br.com.oficina.os.ordemservico.application.dto.OrdemServicoResponse;
import br.com.oficina.os.ordemservico.domain.OrdemServico;
import org.springframework.stereotype.Component;

@Component
public class OrdemServicoMapper {

	public OrdemServicoResponse toResponse(OrdemServico ordemServico) {
		return new OrdemServicoResponse(
			ordemServico.getId(),
			ordemServico.getCodigo(),
			ordemServico.getDescricaoProblema(),
			ordemServico.getObservacoesGerais(),
			ordemServico.getDescricaoServicosExecutados(),
			ordemServico.getStatus(),
			ordemServico.getDataCadastro(),
			ordemServico.getDataSolicitacaoOrcamento(),
			ordemServico.getDataEnvioAprovacao(),
			ordemServico.getDataAprovacao(),
			ordemServico.getDataPagamento(),
			ordemServico.getDataInicioExecucao(),
			ordemServico.getDataFimExecucao(),
			ordemServico.getDataEntregue(),
			ordemServico.getDataCancelada(),
			ordemServico.getOrcamentoId(),
			ordemServico.getPagamentoId()
		);
	}
}
