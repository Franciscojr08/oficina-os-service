package br.com.oficina.os.cliente.entrypoint.controller;

import br.com.oficina.os.cliente.application.dto.ClienteRequest;
import br.com.oficina.os.cliente.application.dto.ClienteResponse;
import br.com.oficina.os.cliente.application.usecase.ClienteUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/clientes")
@RequiredArgsConstructor
@Tag(name = "Clientes", description = "Cadastro de clientes (pessoa física ou jurídica)")
@Validated
public class ClienteController {

	private final ClienteUseCase clienteUseCase;

	@PostMapping
	@Operation(summary = "Cadastrar cliente")
	public ResponseEntity<ClienteResponse> criar(@RequestBody @Valid ClienteRequest request) {
		ClienteResponse response = clienteUseCase.criar(request);
		return ResponseEntity.created(URI.create("/clientes/" + response.id())).body(response);
	}

	@GetMapping
	@Operation(summary = "Listar clientes")
	public ResponseEntity<List<ClienteResponse>> listar() {
		return ResponseEntity.ok(clienteUseCase.listar());
	}

	@GetMapping("/{id}")
	@Operation(summary = "Buscar cliente por id")
	public ResponseEntity<ClienteResponse> buscarPorId(@PathVariable Long id) {
		return ResponseEntity.ok(clienteUseCase.buscarPorId(id));
	}

	@PutMapping("/{id}")
	@Operation(summary = "Alterar cliente")
	public ResponseEntity<ClienteResponse> atualizar(@PathVariable Long id, @RequestBody @Valid ClienteRequest request) {
		return ResponseEntity.ok(clienteUseCase.atualizar(id, request));
	}

	@GetMapping("/documento/{documento}")
	@Operation(summary = "Buscar cliente por CPF ou CNPJ")
	public ResponseEntity<ClienteResponse> buscarPorDocumento(@PathVariable String documento) {
		return ResponseEntity.ok(clienteUseCase.buscarPorDocumento(documento));
	}

	@DeleteMapping("/{id}")
	@Operation(summary = "Inativar cliente (recusado se houver OS ativa)")
	public ResponseEntity<Void> inativar(@PathVariable Long id) {
		clienteUseCase.inativar(id);
		return ResponseEntity.noContent().build();
	}
}
