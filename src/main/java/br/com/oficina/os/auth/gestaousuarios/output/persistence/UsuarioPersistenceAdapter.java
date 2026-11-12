package br.com.oficina.os.auth.gestaousuarios.output.persistence;

import br.com.oficina.os.auth.gestaousuarios.application.gateway.UsuarioGateway;
import br.com.oficina.os.auth.gestaousuarios.domain.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UsuarioPersistenceAdapter implements UsuarioGateway {

	private final UsuarioRepository usuarioRepository;

	@Override
	public Usuario save(Usuario usuario) {
		return usuarioRepository.save(usuario);
	}

	@Override
	public boolean existsByEmail(String email) {
		return usuarioRepository.existsByEmail(email);
	}
}
