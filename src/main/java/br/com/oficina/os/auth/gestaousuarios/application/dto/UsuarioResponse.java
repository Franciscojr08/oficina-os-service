package br.com.oficina.os.auth.gestaousuarios.application.dto;

import br.com.oficina.os.auth.gestaousuarios.domain.RoleUsuario;

import java.time.LocalDateTime;

public record UsuarioResponse(
        Long id,
        String nome,
        String email,
        RoleUsuario role,
        Boolean ativo,
        LocalDateTime dataCriacao,
        LocalDateTime dataAtualizacao
) {
}
