package br.com.oficina.os.ordemservico.application;

import br.com.oficina.os.ordemservico.application.dto.HistoricoOrdemServicoResponse;
import br.com.oficina.os.ordemservico.application.gateway.HistoricoOrdemServicoGateway;
import br.com.oficina.os.ordemservico.domain.HistoricoOrdemServico;
import br.com.oficina.os.ordemservico.domain.OrdemServico;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class HistoricoOrdemServicoService {

	private final HistoricoOrdemServicoGateway historicoOrdemServicoGateway;

	/**
	 * Registra a mudança de status. {@code origem} identifica quem a provocou: "API" para chamadas
	 * REST ou o nome do evento recebido (por exemplo, "PagamentoConfirmado").
	 */
	public void registrar(OrdemServico ordemServico, StatusOrdemServico status, String origem) {
		HistoricoOrdemServico historicoOrdemServico = new HistoricoOrdemServico();
		historicoOrdemServico.setOrdemServico(ordemServico);
		historicoOrdemServico.setStatus(status);
		historicoOrdemServico.setObservacao(status.getDescricao());
		historicoOrdemServico.setOrigem(origem);
		historicoOrdemServicoGateway.save(historicoOrdemServico);
	}

	/** Histórico da OS em ordem cronológica. Quem chama garante que a OS existe. */
	public List<HistoricoOrdemServicoResponse> listar(Long ordemServicoId) {
		return historicoOrdemServicoGateway.listarPorOrdemServico(ordemServicoId)
				.stream()
				.map(historico -> new HistoricoOrdemServicoResponse(
						historico.getStatus(),
						historico.getStatus().getDescricao(),
						historico.getOrigem(),
						historico.getDataCadastro()))
				.toList();
	}
}
