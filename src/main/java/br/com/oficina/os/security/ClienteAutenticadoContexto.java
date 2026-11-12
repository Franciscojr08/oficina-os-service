package br.com.oficina.os.security;

import br.com.oficina.os.security.domain.ClienteAutenticado;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Identifica se a requisição atual vem de um cliente (token emitido pela function de autenticação
 * por CPF). Operadores da oficina não têm restrição por cliente; clientes só enxergam as próprias OS.
 */
public final class ClienteAutenticadoContexto {

	private ClienteAutenticadoContexto() {
	}

	/** Id do cliente autenticado, ou vazio quando a requisição é de um operador ou anônima. */
	public static Optional<Long> clienteId() {
		Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacao != null && autenticacao.getPrincipal() instanceof ClienteAutenticado cliente) {
			return Optional.of(cliente.id());
		}
		return Optional.empty();
	}
}
