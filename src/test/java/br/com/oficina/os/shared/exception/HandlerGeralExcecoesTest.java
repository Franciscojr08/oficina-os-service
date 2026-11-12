package br.com.oficina.os.shared.exception;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.oficina.os.config.ControllerIntegrationTestSupport;
import br.com.oficina.os.config.IntegrationTest;
import br.com.oficina.os.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Erros gerados pelo próprio Spring MVC (antes de chegar à regra de negócio) também respondem em
 * application/problem+json, com mensagem em português.
 */
@IntegrationTest
class HandlerGeralExcecoesTest extends ControllerIntegrationTestSupport {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtService jwtService;

	@Test
	void statusInexistenteNaUrlDeveResponder400ComValoresAceitos() throws Exception {
		executar(get("/ordens/status/{status}", "EM_PREPARACAO"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.title").value("Requisicao invalida"))
				.andExpect(jsonPath("$.detail").value(containsString("Valor inválido para 'status': EM_PREPARACAO")))
				.andExpect(jsonPath("$.detail").value(containsString("AGUARDANDO_ITENS")));
	}

	@Test
	void idNaoNumericoDeveResponder400() throws Exception {
		executar(get("/ordens/{id}", "abc"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("Valor inválido para 'id': abc"));
	}

	@Test
	void jsonMalformadoDeveResponder400() throws Exception {
		executar(post("/ordens").contentType(MediaType.APPLICATION_JSON).content("{ isto não é json"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.detail").value("Corpo da requisição ausente ou com JSON inválido"));
	}

	@Test
	void corpoAusenteDeveResponder400() throws Exception {
		executar(post("/ordens").contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("Corpo da requisição ausente ou com JSON inválido"));
	}

	@Test
	void rotaInexistenteDeveResponder404EmPortugues() throws Exception {
		executar(patch("/ordens/{id}/iniciar-execucao", 1))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.detail").value("Rota inexistente: /ordens/1/iniciar-execucao"));
	}

	@Test
	void metodoNaoSuportadoDeveResponder405() throws Exception {
		executar(delete("/ordens/{id}", 1))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.detail").value("Método DELETE não suportado nesta rota"));
	}

	private ResultActions executar(MockHttpServletRequestBuilder requisicao) throws Exception {
		return mockMvc.perform(requisicao.header("Authorization", bearerTokenAdmin(jwtService)));
	}
}
