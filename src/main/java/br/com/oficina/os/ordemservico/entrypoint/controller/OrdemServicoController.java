package br.com.oficina.os.ordemservico.entrypoint.controller;

import br.com.oficina.os.ordemservico.application.StatusOrdemServico;
import br.com.oficina.os.ordemservico.application.dto.HistoricoOrdemServicoResponse;
import br.com.oficina.os.ordemservico.application.dto.OrdemServicoRequest;
import br.com.oficina.os.ordemservico.application.dto.OrdemServicoResponse;
import br.com.oficina.os.ordemservico.application.dto.StatusOrdemServicoResponse;
import br.com.oficina.os.ordemservico.application.usecase.OrdemServicoUseCase;
import br.com.oficina.os.ordemservico.itens.application.dto.ItemOrdemServicoRequest;
import br.com.oficina.os.ordemservico.itens.application.dto.ListaItensOrdemServicoResponse;
import br.com.oficina.os.ordemservico.itens.application.usecase.ItemOrdemServicoUseCase;
import br.com.oficina.os.ordemservico.servicos.application.dto.ListaServicosOrdemServicoResponse;
import br.com.oficina.os.ordemservico.servicos.application.dto.ServicoOrdemServicoRequest;
import br.com.oficina.os.ordemservico.servicos.application.usecase.ServicoOrdemServicoUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * Ciclo de vida da OS exposto pela API. Aprovação, pagamento e execução não têm endpoint aqui: são
 * do Billing e da Execução, e chegam ao OS Service por eventos da Saga.
 */
@RestController
@RequestMapping("/ordens")
@RequiredArgsConstructor
@Validated
@Tag(name = "Ordens de serviço", description = "Abertura, diagnóstico, acompanhamento, cancelamento e entrega da OS")
public class OrdemServicoController {

	private static final String NAO_ENCONTRADA = "Ordem de serviço não encontrada";
	private static final String STATUS_NAO_PERMITE = "O status atual da OS não permite a ação";

	private final OrdemServicoUseCase ordemServicoUseCase;
	private final ItemOrdemServicoUseCase itemOrdemServicoUseCase;
	private final ServicoOrdemServicoUseCase servicoOrdemServicoUseCase;

	@GetMapping
	@Operation(summary = "Listar ordens de serviço",
			description = "Lista as OS em andamento, das mais antigas para as mais recentes.")
	public ResponseEntity<List<OrdemServicoResponse>> listar() {
		return ResponseEntity.ok(ordemServicoUseCase.listar());
	}

	@GetMapping("/{id}")
	@Operation(summary = "Buscar ordem de serviço por id", responses = {
		@ApiResponse(responseCode = "200", description = "Ordem de serviço encontrada"),
		@ApiResponse(responseCode = "404", description = NAO_ENCONTRADA)
	})
	public ResponseEntity<OrdemServicoResponse> buscarPorId(@PathVariable Long id) {
		return ResponseEntity.ok(ordemServicoUseCase.buscarPorId(id));
	}

	@GetMapping("/cliente/{id}")
	@Operation(summary = "Listar ordens de serviço de um cliente")
	public ResponseEntity<List<OrdemServicoResponse>> listarPorCliente(@PathVariable Long id) {
		return ResponseEntity.ok(ordemServicoUseCase.listarPorCliente(id));
	}

	@GetMapping("/codigo/{codigo}")
	@Operation(summary = "Buscar ordem de serviço pelo código", description = "Código no formato OS-AAAA-NNNNNN.")
	public ResponseEntity<List<OrdemServicoResponse>> listarPorCodigo(@PathVariable String codigo) {
		return ResponseEntity.ok(ordemServicoUseCase.listarPorCodigo(codigo));
	}

	@GetMapping("/status/{status}")
	@Operation(summary = "Listar ordens de serviço por status", responses = {
		@ApiResponse(responseCode = "200", description = "Ordens de serviço no status informado"),
		@ApiResponse(responseCode = "400", description = "Status inexistente")
	})
	public ResponseEntity<List<OrdemServicoResponse>> listarPorStatus(@PathVariable StatusOrdemServico status) {
		return ResponseEntity.ok(ordemServicoUseCase.listarPorStatus(status));
	}

	@PostMapping
	@Operation(summary = "Abrir ordem de serviço",
			description = "Abre a OS em RECEBIDA com os serviços e itens informados.", responses = {
				@ApiResponse(responseCode = "201", description = "Ordem de serviço aberta"),
				@ApiResponse(responseCode = "400", description = "Dados inválidos"),
				@ApiResponse(responseCode = "404", description = "Cliente, veículo, serviço ou item inexistente"),
				@ApiResponse(responseCode = "422", description = "Cadastro inativo ou veículo de outro cliente")
			})
	public ResponseEntity<OrdemServicoResponse> criar(@RequestBody @Valid OrdemServicoRequest request) {
		OrdemServicoResponse response = ordemServicoUseCase.criar(request);
		return ResponseEntity
				.created(URI.create("/ordens/" + response.id()))
				.body(response);
	}

	@PutMapping("/{id}")
	@Operation(summary = "Alterar ordem de serviço",
			description = "Permitido só em RECEBIDA e EM_DIAGNOSTICO, antes do pedido de orçamento.", responses = {
				@ApiResponse(responseCode = "200", description = "Ordem de serviço alterada"),
				@ApiResponse(responseCode = "400", description = "Dados inválidos"),
				@ApiResponse(responseCode = "404", description = NAO_ENCONTRADA),
				@ApiResponse(responseCode = "422", description = STATUS_NAO_PERMITE)
			})
	public ResponseEntity<OrdemServicoResponse> atualizar(
			@PathVariable Long id,
			@RequestBody @Valid OrdemServicoRequest request
	) {
		return ResponseEntity.ok(ordemServicoUseCase.atualizar(id, request));
	}

	@PostMapping("/{id}/itens")
	@Operation(summary = "Adicionar item (peça ou insumo) à OS", description = "Permitido só em EM_DIAGNOSTICO.",
			responses = {
				@ApiResponse(responseCode = "200", description = "Itens da OS após a inclusão"),
				@ApiResponse(responseCode = "404", description = "OS ou item inexistente"),
				@ApiResponse(responseCode = "422", description = STATUS_NAO_PERMITE)
			})
	public ResponseEntity<ListaItensOrdemServicoResponse> adicionarItem(
			@PathVariable Long id,
			@RequestBody @Valid ItemOrdemServicoRequest request
	) {
		return ResponseEntity.ok(itemOrdemServicoUseCase.adicionarItem(id, request));
	}

	@GetMapping("/{id}/itens")
	@Operation(summary = "Listar itens da OS")
	public ResponseEntity<ListaItensOrdemServicoResponse> listarItensPorOrdemServico(@PathVariable Long id) {
		return ResponseEntity.ok(itemOrdemServicoUseCase.listarItensPorOrdemServico(id));
	}

	@PostMapping("/{id}/servicos")
	@Operation(summary = "Adicionar serviço à OS", description = "Permitido só em EM_DIAGNOSTICO.", responses = {
		@ApiResponse(responseCode = "200", description = "Serviços da OS após a inclusão"),
		@ApiResponse(responseCode = "404", description = "OS ou serviço inexistente"),
		@ApiResponse(responseCode = "422", description = STATUS_NAO_PERMITE)
	})
	public ResponseEntity<ListaServicosOrdemServicoResponse> adicionarServico(
			@PathVariable Long id,
			@RequestBody @Valid ServicoOrdemServicoRequest request
	) {
		return ResponseEntity.ok(servicoOrdemServicoUseCase.adicionarServico(id, request));
	}

	@GetMapping("/{id}/servicos")
	@Operation(summary = "Listar serviços da OS")
	public ResponseEntity<ListaServicosOrdemServicoResponse> listarServicosPorOrdemServico(@PathVariable Long id) {
		return ResponseEntity.ok(servicoOrdemServicoUseCase.listarServicosPorOrdemServico(id));
	}

	@PatchMapping("/{id}/iniciar-diagnostico")
	@Operation(summary = "Iniciar diagnóstico", description = "RECEBIDA → EM_DIAGNOSTICO.", responses = {
		@ApiResponse(responseCode = "200", description = "Diagnóstico iniciado"),
		@ApiResponse(responseCode = "404", description = NAO_ENCONTRADA),
		@ApiResponse(responseCode = "422", description = STATUS_NAO_PERMITE)
	})
	public ResponseEntity<OrdemServicoResponse> iniciarDiagnostico(@PathVariable Long id) {
		return ResponseEntity.ok(ordemServicoUseCase.iniciarDiagnostico(id));
	}

	@PatchMapping("/{id}/solicitar-aprovacao")
	@Operation(summary = "Concluir diagnóstico e pedir orçamento",
			description = "Exige ao menos um item e um serviço. A OS segue em EM_DIAGNOSTICO enquanto o Billing "
					+ "gera o orçamento (a OS fica travada para alteração) e vai para AGUARDANDO_APROVACAO "
					+ "quando ele chega.",
			responses = {
				@ApiResponse(responseCode = "200", description = "Orçamento solicitado"),
				@ApiResponse(responseCode = "404", description = NAO_ENCONTRADA),
				@ApiResponse(responseCode = "422", description = "OS sem item ou serviço, ou status não permite")
			})
	public ResponseEntity<OrdemServicoResponse> solicitarAprovacao(@PathVariable Long id) {
		return ResponseEntity.ok(ordemServicoUseCase.solicitarAprovacao(id));
	}

	@PatchMapping("/{id}/aprovar")
	@Operation(summary = "Aprovar orçamento",
			description = "O cliente aprovou o orçamento (AGUARDANDO_APROVACAO). O OS Service pede a cobrança ao "
					+ "Billing (CobrarPagamento); a OS só vai para APROVADA quando o pagamento for confirmado.",
			responses = {
				@ApiResponse(responseCode = "200", description = "Aprovação registrada, cobrança solicitada"),
				@ApiResponse(responseCode = "404", description = NAO_ENCONTRADA),
				@ApiResponse(responseCode = "422", description = "OS não está aguardando a decisão sobre o orçamento")
			})
	public ResponseEntity<OrdemServicoResponse> aprovar(@PathVariable Long id) {
		return ResponseEntity.ok(ordemServicoUseCase.aprovar(id));
	}

	@PatchMapping("/{id}/reprovar")
	@Operation(summary = "Reprovar orçamento",
			description = "O cliente reprovou o orçamento (AGUARDANDO_APROVACAO → CANCELADA). Nada foi cobrado.",
			responses = {
				@ApiResponse(responseCode = "200", description = "Orçamento reprovado, OS cancelada"),
				@ApiResponse(responseCode = "404", description = NAO_ENCONTRADA),
				@ApiResponse(responseCode = "422", description = "OS não está aguardando a decisão sobre o orçamento")
			})
	public ResponseEntity<OrdemServicoResponse> reprovar(@PathVariable Long id) {
		return ResponseEntity.ok(ordemServicoUseCase.reprovar(id));
	}

	@PatchMapping("/{id}/cancelar")
	@Operation(summary = "Cancelar ordem de serviço",
			description = "Pedido do cliente ou da oficina, permitido até antes do início da execução. "
					+ "Sem pagamento, a OS é cancelada na hora (200). Com pagamento, a Saga pede à Execução "
					+ "que devolva o estoque e ao Billing que estorne; a OS continua no status atual e vai "
					+ "para CANCELADA quando o estorno for confirmado (202).",
			responses = {
				@ApiResponse(responseCode = "200", description = "Ordem de serviço cancelada"),
				@ApiResponse(responseCode = "202", description = "Cancelamento aceito, aguardando o estorno do pagamento"),
				@ApiResponse(responseCode = "404", description = NAO_ENCONTRADA),
				@ApiResponse(responseCode = "422", description = "Execução já iniciada, OS encerrada ou cancelamento já em andamento")
			})
	public ResponseEntity<OrdemServicoResponse> cancelar(@PathVariable Long id) {
		OrdemServicoResponse response = ordemServicoUseCase.cancelar(id);
		HttpStatus status = response.status() == StatusOrdemServico.CANCELADA ? HttpStatus.OK : HttpStatus.ACCEPTED;
		return ResponseEntity.status(status).body(response);
	}

	@PatchMapping("/{id}/entregar")
	@Operation(summary = "Entregar veículo", description = "FINALIZADA → ENTREGUE.", responses = {
		@ApiResponse(responseCode = "200", description = "Ordem de serviço entregue"),
		@ApiResponse(responseCode = "404", description = NAO_ENCONTRADA),
		@ApiResponse(responseCode = "422", description = STATUS_NAO_PERMITE)
	})
	public ResponseEntity<OrdemServicoResponse> entregarOrdemServico(@PathVariable Long id) {
		return ResponseEntity.ok(ordemServicoUseCase.entregar(id));
	}

	@GetMapping("/{id}/status")
	@Operation(summary = "Consultar status atual", responses = {
		@ApiResponse(responseCode = "200", description = "Status atual da OS"),
		@ApiResponse(responseCode = "404", description = NAO_ENCONTRADA)
	})
	public ResponseEntity<StatusOrdemServicoResponse> status(@PathVariable Long id) {
		return ResponseEntity.ok(ordemServicoUseCase.consultarStatus(id));
	}

	@GetMapping("/{id}/historico")
	@Operation(summary = "Consultar histórico de status",
			description = "Mudanças de status em ordem cronológica, com a origem de cada uma: API ou o evento da Saga.",
			responses = {
				@ApiResponse(responseCode = "200", description = "Histórico da OS"),
				@ApiResponse(responseCode = "404", description = NAO_ENCONTRADA)
			})
	public ResponseEntity<List<HistoricoOrdemServicoResponse>> historico(@PathVariable Long id) {
		return ResponseEntity.ok(ordemServicoUseCase.consultarHistorico(id));
	}
}
