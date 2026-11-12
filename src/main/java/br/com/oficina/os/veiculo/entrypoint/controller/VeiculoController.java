package br.com.oficina.os.veiculo.entrypoint.controller;

import br.com.oficina.os.veiculo.application.dto.VeiculoRequest;
import br.com.oficina.os.veiculo.application.dto.VeiculoResponse;
import br.com.oficina.os.veiculo.application.usecase.VeiculoUseCase;
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
@RequestMapping("/veiculos")
@RequiredArgsConstructor
@Tag(name = "Veículos", description = "Cadastro de veículos dos clientes")
@Validated
public class VeiculoController {

    private final VeiculoUseCase veiculoUseCase;

    @PostMapping
    @Operation(summary = "Cadastrar veículo")
    public ResponseEntity<VeiculoResponse> criar(@RequestBody @Valid VeiculoRequest request) {
        VeiculoResponse response = veiculoUseCase.criar(request);
        return ResponseEntity
                .created(URI.create("/veiculos/" + response.id()))
                .body(response);
    }

    @GetMapping
    @Operation(summary = "Listar veículos")
    public ResponseEntity<List<VeiculoResponse>> listar() {
        return ResponseEntity.ok(veiculoUseCase.listar());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar veículo por id")
    public ResponseEntity<VeiculoResponse> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(veiculoUseCase.buscarPorId(id));
    }

    @GetMapping("/cliente/{clienteId}")
    @Operation(summary = "Listar veículos de um cliente")
    public ResponseEntity<List<VeiculoResponse>> listarPorCliente(@PathVariable Long clienteId) {
        return ResponseEntity.ok(veiculoUseCase.listarPorCliente(clienteId));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Alterar veículo")
    public ResponseEntity<VeiculoResponse> atualizar(
            @PathVariable Long id,
            @RequestBody @Valid VeiculoRequest request) {
        return ResponseEntity.ok(veiculoUseCase.atualizar(id, request));
    }

    @GetMapping("/placa/{placa}")
    @Operation(summary = "Buscar veículo pela placa (padrão antigo ou Mercosul)")
    public ResponseEntity<VeiculoResponse> buscarPorPlaca(@PathVariable String placa) {
        return ResponseEntity.ok(veiculoUseCase.buscarPorPlaca(placa));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Inativar veículo (recusado se houver OS ativa)")
    public ResponseEntity<Void> inativar(@PathVariable Long id) {
        veiculoUseCase.inativar(id);
        return ResponseEntity.noContent().build();
    }
}
