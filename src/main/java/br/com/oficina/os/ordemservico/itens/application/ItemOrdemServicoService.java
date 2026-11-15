package br.com.oficina.os.ordemservico.itens.application;

import static br.com.oficina.os.shared.exception.ExceptionMessage.CANNOT_ADD_ITEM_WITH_ORDER_OUTSIDE_DIAGNOSIS;
import static br.com.oficina.os.shared.exception.ExceptionMessage.SERVICE_ORDER_NOT_FOUND;

import br.com.oficina.os.ordemservico.application.OrdemServicoStatusService;
import br.com.oficina.os.ordemservico.application.StatusOrdemServico;
import br.com.oficina.os.ordemservico.application.gateway.OrdemServicoGateway;
import br.com.oficina.os.ordemservico.domain.OrdemServico;
import br.com.oficina.os.ordemservico.itens.application.dto.ItemOrdemServicoRequest;
import br.com.oficina.os.ordemservico.itens.application.dto.ItemOrdemServicoResponse;
import br.com.oficina.os.ordemservico.itens.application.dto.ListaItensOrdemServicoResponse;
import br.com.oficina.os.ordemservico.itens.application.gateway.ItemOrdemServicoGateway;
import br.com.oficina.os.ordemservico.itens.domain.ItemOrdemServico;
import br.com.oficina.os.shared.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ItemOrdemServicoService {

	private final OrdemServicoGateway ordemServicoGateway;
	private final ItemOrdemServicoGateway itemOrdemServicoGateway;
	private final OrdemServicoStatusService ordemServicoStatusService;

	@Transactional(readOnly = true)
	public ListaItensOrdemServicoResponse listarItensPorOrdemServico(Long id) {
		List<ItemOrdemServico> itemOrdemServicoList = itemOrdemServicoGateway.findByOrdemServicoId(id);

		List<ItemOrdemServicoResponse> itens = itemOrdemServicoList.stream()
				.map(this::toItemResponse)
				.toList();

		return new ListaItensOrdemServicoResponse(itens);
	}

	@Transactional
	public void adicionarItem(Long id, ItemOrdemServicoRequest request) {
		OrdemServico ordemServico = buscarOrdemServicoPorId(id);

		String mensagem = String.format(
			CANNOT_ADD_ITEM_WITH_ORDER_OUTSIDE_DIAGNOSIS,
			StatusOrdemServico.EM_DIAGNOSTICO.getDescricao()
		);
		ordemServicoStatusService.validarEmDiagnostico(ordemServico, mensagem);

		adicionarItemNaOrdem(ordemServico, request);
	}

	@Transactional
	public void adicionarItemDuranteCadastro(OrdemServico ordemServico, ItemOrdemServicoRequest request) {
		adicionarItemNaOrdem(ordemServico, request);
	}

	private void adicionarItemNaOrdem(OrdemServico ordemServico, ItemOrdemServicoRequest request) {
		// O catálogo é do Billing e o estoque é da Execução: aqui não se checa se o item existe nem se há
		// saldo. O Billing valida o id ao orçar e a Execução reserva as peças e, se faltar, avisa a Saga.
		ItemOrdemServico itemOrdemServico = new ItemOrdemServico();
		preencherItemOrdemServico(itemOrdemServico, ordemServico, request);

		itemOrdemServicoGateway.save(itemOrdemServico);
	}

	private OrdemServico buscarOrdemServicoPorId(Long id) {
		return ordemServicoGateway.findById(id)
				.orElseThrow(() -> new RecursoNaoEncontradoException(SERVICE_ORDER_NOT_FOUND));
	}

	private void preencherItemOrdemServico(
			ItemOrdemServico itemOrdemServico,
			OrdemServico ordemServico,
			ItemOrdemServicoRequest request
	) {
		itemOrdemServico.setOrdemServico(ordemServico);
		itemOrdemServico.setItemId(request.itemId());
		itemOrdemServico.setItemNome(request.nome().strip());
		itemOrdemServico.setQuantidade(request.quantidade());
	}

	private ItemOrdemServicoResponse toItemResponse(ItemOrdemServico itemOS) {
		return new ItemOrdemServicoResponse(
			itemOS.getOrdemServico().getCodigo(),
			itemOS.getItemId(),
			itemOS.getItemNome(),
			itemOS.getQuantidade()
		);
	}
}
