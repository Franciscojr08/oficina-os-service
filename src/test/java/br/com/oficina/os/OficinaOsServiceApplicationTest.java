package br.com.oficina.os;

import br.com.oficina.os.config.ControllerIntegrationTestSupport;
import br.com.oficina.os.config.TestcontainersConfig;
import br.com.oficina.os.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.client.RestTestClient;

/** Sobe a aplicação num servidor real (porta aleatória), como em produção, com despacho de erro. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfig.class)
class OficinaOsServiceApplicationTest extends ControllerIntegrationTestSupport {

	@Autowired
	private RestTestClient client;

	@Autowired
	private JwtService jwtService;

	@Test
	void deveSubirAplicacaoComHealthUp() {
		client.get().uri("/actuator/health")
				.exchange()
				.expectStatus().isOk()
				.expectBody().jsonPath("$.status").isEqualTo("UP");
	}

	@Test
	void deveExporProbesDeLivenessEReadiness() {
		client.get().uri("/actuator/health/liveness").exchange().expectStatus().isOk();
		client.get().uri("/actuator/health/readiness").exchange().expectStatus().isOk();
	}

	/**
	 * Regressão: o despacho para /error não passa pelo filtro JWT, e sem liberar /error uma rota
	 * inexistente chegava ao cliente autenticado como 403.
	 */
	@Test
	void rotaInexistenteDeveResponder404ParaClienteAutenticado() {
		client.patch().uri("/ordens/1/aprovar")
				.header("Authorization", bearerTokenAdmin(jwtService))
				.exchange()
				.expectStatus().isNotFound();
	}

	@Test
	void rotaProtegidaSemTokenDeveSerRecusada() {
		client.get().uri("/ordens")
				.exchange()
				.expectStatus().isForbidden();
	}
}
