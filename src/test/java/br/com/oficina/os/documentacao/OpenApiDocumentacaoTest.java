package br.com.oficina.os.documentacao;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.oficina.os.config.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** O contrato OpenAPI é público, traz os endpoints novos e não traz mais os que saíram do serviço. */
@IntegrationTest
class OpenApiDocumentacaoTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void contratoDaOrdemDeServicoDeveRefletirAEtapa3() throws Exception {
		mockMvc.perform(get("/api-docs/ordem-servico"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.info.title").value("Oficina - OS Service"))
				.andExpect(jsonPath("$.paths", hasKey("/ordens/{id}/cancelar")))
				.andExpect(jsonPath("$.paths", hasKey("/ordens/{id}/historico")))
				.andExpect(jsonPath("$.paths", hasKey("/ordens/{id}")))
				.andExpect(jsonPath("$.paths['/ordens/{id}/cancelar'].patch.summary").value("Cancelar ordem de serviço"))
				.andExpect(jsonPath("$.paths", hasKey("/ordens/{id}/aprovar")))
				.andExpect(jsonPath("$.paths", hasKey("/ordens/{id}/reprovar")))
				.andExpect(jsonPath("$.paths", not(hasKey("/ordens/{id}/iniciar-execucao"))));
	}

	@Test
	void todosOsGruposDevemTerDocumentacao() throws Exception {
		for (String grupo : new String[] {"autenticacao", "gestao-usuarios", "cliente", "veiculo",
				"ordem-servico", "api-publica"}) {
			mockMvc.perform(get("/api-docs/" + grupo))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.tags[0].name").exists());
		}
	}

	@Test
	void swaggerUiDeveSerPublico() throws Exception {
		mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
	}
}
