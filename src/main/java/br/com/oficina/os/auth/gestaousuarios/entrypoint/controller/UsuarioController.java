package br.com.oficina.os.auth.gestaousuarios.entrypoint.controller;

import br.com.oficina.os.auth.gestaousuarios.application.dto.UsuarioRequest;
import br.com.oficina.os.auth.gestaousuarios.application.dto.UsuarioResponse;
import br.com.oficina.os.auth.gestaousuarios.application.usecase.UsuarioUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/usuarios")
@RequiredArgsConstructor
@Tag(name = "Usuários", description = "Usuários da oficina que acessam a API")
public class UsuarioController {

	private final UsuarioUseCase usuarioUseCase;

    @PostMapping
    @Operation(summary = "Cadastrar usuário")
    public ResponseEntity<UsuarioResponse> criar(@RequestBody @Valid UsuarioRequest request) {
	    UsuarioResponse response = usuarioUseCase.criar(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
