package br.com.oficina.os.security;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.oficina.os.security.domain.ClienteAutenticado;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

class ClienteAutenticadoContextoTest {

	@AfterEach
	void limpar() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void deveRetornarIdQuandoRequisicaoEDeCliente() {
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
				new ClienteAutenticado(7L, "52998224725", "Ana"), null,
				List.of(new SimpleGrantedAuthority("ROLE_CLIENTE"))));

		assertThat(ClienteAutenticadoContexto.clienteId()).contains(7L);
	}

	@Test
	void deveSerVazioParaOperador() {
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
				"admin@oficina.com", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

		assertThat(ClienteAutenticadoContexto.clienteId()).isEmpty();
	}

	@Test
	void deveSerVazioSemAutenticacao() {
		assertThat(ClienteAutenticadoContexto.clienteId()).isEmpty();
	}
}
