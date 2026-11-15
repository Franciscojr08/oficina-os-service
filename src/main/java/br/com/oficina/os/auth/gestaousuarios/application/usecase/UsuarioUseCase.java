package br.com.oficina.os.auth.gestaousuarios.application.usecase;

import br.com.oficina.os.auth.gestaousuarios.application.UsuarioService;
import br.com.oficina.os.auth.gestaousuarios.application.dto.UsuarioRequest;
import br.com.oficina.os.auth.gestaousuarios.application.dto.UsuarioResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UsuarioUseCase {

	private final UsuarioService usuarioService;

	public UsuarioResponse criar(UsuarioRequest request) {
		return usuarioService.criar(request);
	}
}
