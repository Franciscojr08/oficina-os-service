package br.com.oficina.os.auth.gestaousuarios.application.gateway;

import br.com.oficina.os.auth.gestaousuarios.domain.Usuario;

public interface UsuarioGateway {

	Usuario save(Usuario usuario);

	boolean existsByEmail(String email);
}
