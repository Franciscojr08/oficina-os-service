package br.com.oficina.os.ordemservico.servicos.application;

import static br.com.oficina.os.shared.exception.ExceptionMessage.CANNOT_ADD_SERVICE_WITH_ORDER_OUTSIDE_DIAGNOSIS;
import static br.com.oficina.os.shared.exception.ExceptionMessage.SERVICE_ORDER_NOT_FOUND;

import br.com.oficina.os.ordemservico.application.OrdemServicoStatusService;
import br.com.oficina.os.ordemservico.application.StatusOrdemServico;
import br.com.oficina.os.ordemservico.application.gateway.OrdemServicoGateway;
import br.com.oficina.os.ordemservico.domain.OrdemServico;
import br.com.oficina.os.ordemservico.servicos.application.dto.ListaServicosOrdemServicoResponse;
import br.com.oficina.os.ordemservico.servicos.application.dto.ServicoOrdemServicoRequest;
import br.com.oficina.os.ordemservico.servicos.application.dto.ServicoOrdemServicoResponse;
import br.com.oficina.os.ordemservico.servicos.application.gateway.ServicoOrdemServicoGateway;
import br.com.oficina.os.ordemservico.servicos.domain.ServicoOrdemServico;
import br.com.oficina.os.shared.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ServicoOrdemServicoService {

	private final OrdemServicoGateway ordemServicoGateway;
	private final ServicoOrdemServicoGateway servicoOrdemServicoGateway;
	private final OrdemServicoStatusService ordemServicoStatusService;

	@Transactional(readOnly = true)
	public ListaServicosOrdemServicoResponse listarServicosPorOrdemServico(Long id) {
		List<ServicoOrdemServico> lista = servicoOrdemServicoGateway.findByOrdemServicoId(id);

		List<ServicoOrdemServicoResponse> servicos = lista.stream()
				.map(this::toServicoResponse)
				.toList();

		return new ListaServicosOrdemServicoResponse(servicos);
	}

	@Transactional
	public void adicionarServico(Long id, ServicoOrdemServicoRequest request) {
		OrdemServico ordemServico = buscarOrdemServicoPorId(id);

		String mensagem = String.format(
			CANNOT_ADD_SERVICE_WITH_ORDER_OUTSIDE_DIAGNOSIS,
			StatusOrdemServico.EM_DIAGNOSTICO.getDescricao()
		);
		ordemServicoStatusService.validarEmDiagnostico(ordemServico, mensagem);

		adicionarServicoNaOrdem(ordemServico, request);
	}

	@Transactional
	public void adicionarServicoDuranteCadastro(OrdemServico ordemServico, ServicoOrdemServicoRequest request) {
		adicionarServicoNaOrdem(ordemServico, request);
	}

	private void adicionarServicoNaOrdem(OrdemServico ordemServico, ServicoOrdemServicoRequest request) {
		// O catálogo é do Billing: o OS guarda o id e o nome que recebeu, e o Billing valida o id ao orçar.
		ServicoOrdemServico servicoOrdemServico = new ServicoOrdemServico();
		preencherServicoOrdemServico(servicoOrdemServico, ordemServico, request);

		servicoOrdemServicoGateway.save(servicoOrdemServico);
	}

	private OrdemServico buscarOrdemServicoPorId(Long id) {
		return ordemServicoGateway.findById(id)
				.orElseThrow(() -> new RecursoNaoEncontradoException(SERVICE_ORDER_NOT_FOUND));
	}

	private void preencherServicoOrdemServico(
			ServicoOrdemServico servicoOrdemServico,
			OrdemServico ordemServico,
			ServicoOrdemServicoRequest request
	) {
		servicoOrdemServico.setOrdemServico(ordemServico);
		servicoOrdemServico.setServicoId(request.servicoId());
		servicoOrdemServico.setServicoNome(request.nome().strip());
	}

	private ServicoOrdemServicoResponse toServicoResponse(ServicoOrdemServico servicoOS) {
		return new ServicoOrdemServicoResponse(
				servicoOS.getOrdemServico().getCodigo(),
				servicoOS.getServicoId(),
				servicoOS.getServicoNome(),
				servicoOS.getDataCadastro()
		);
	}
}
