package br.com.oficina.os.apoio;

import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.oficina.os.config.DadosDeTeste;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Opera o OS Service pela API (como o atendente) e lê o estado da OS e da Saga no banco, para os
 * testes de integração e os cenários BDD.
 */
public class OficinaApi {

	private final MockMvc mockMvc;
	private final JsonMapper jsonMapper;
	private final JdbcTemplate jdbcTemplate;
	private final String bearer;

	public OficinaApi(MockMvc mockMvc, JsonMapper jsonMapper, JdbcTemplate jdbcTemplate, String bearer) {
		this.mockMvc = mockMvc;
		this.jsonMapper = jsonMapper;
		this.jdbcTemplate = jdbcTemplate;
		this.bearer = bearer;
	}

	/** OS com um item (2 unidades) e um serviço, diagnosticada e com orçamento pedido. */
	public long abrirOsEPedirOrcamento() throws Exception {
		long osId = abrirOsDiagnosticada("Filtro", 2, "Revisão");
		pedirOrcamento(osId);
		return osId;
	}

	/** Cadastra cliente e veículo, abre a OS com um item e um serviço e inicia o diagnóstico. */
	public long abrirOsDiagnosticada(String item, int quantidade, String servico) throws Exception {
		long clienteId = criar("/clientes", Map.of(
				"nome", "Cliente Teste", "cpfCnpj", DadosDeTeste.cpf(), "email", "cliente@example.com", "cep", "01001000",
				"logradouro", "Praça da Sé", "bairro", "Sé", "cidade", "São Paulo", "uf", "SP",
				"data_nascimento", "1990-05-10"));
		long veiculoId = criar("/veiculos", Map.of(
				"clienteId", clienteId, "placa", DadosDeTeste.placa(), "marca", "Fiat", "modelo", "Argo", "ano", 2022));
		// O catálogo é do Billing: o OS só guarda o id e o nome que recebe
		long servicoId = ThreadLocalRandom.current().nextLong(1, 1_000_000);
		long itemId = ThreadLocalRandom.current().nextLong(1, 1_000_000);
		long osId = criar("/ordens", Map.of(
				"descricaoServicosExecutados", "Revisão", "clienteId", clienteId, "veiculoId", veiculoId,
				"servicos", List.of(Map.of("servicoId", servicoId, "nome", servico)),
				"itens", List.of(Map.of("itemId", itemId, "nome", item, "quantidade", quantidade))));
		executar(patch("/ordens/{id}/iniciar-diagnostico", osId)).andExpect(status().isOk());
		return osId;
	}

	public void pedirOrcamento(long osId) throws Exception {
		executar(patch("/ordens/{id}/solicitar-aprovacao", osId)).andExpect(status().isOk());
	}

	/** O cliente (ou o atendente por ele) aprova o orçamento: o OS Service pede a cobrança ao Billing. */
	public void aprovar(long osId) throws Exception {
		executar(patch("/ordens/{id}/aprovar", osId)).andExpect(status().isOk());
	}

	public void reprovar(long osId) throws Exception {
		executar(patch("/ordens/{id}/reprovar", osId)).andExpect(status().isOk());
	}

	public ResultActions executar(MockHttpServletRequestBuilder requisicao) throws Exception {
		return mockMvc.perform(requisicao.header("Authorization", bearer));
	}

	public long criar(String rota, Object corpo) throws Exception {
		String resposta = mockMvc.perform(post(rota)
						.header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content(jsonMapper.writeValueAsString(corpo)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return jsonMapper.readTree(resposta).get("id").asLong();
	}

	/** Histórico da OS pela API, em ordem cronológica. */
	public JsonNode historico(long osId) throws Exception {
		String resposta = executar(get("/ordens/{id}/historico", osId))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return jsonMapper.readTree(resposta);
	}

	public void aguardarStatus(long osId, String status) {
		await().atMost(SimuladorServicosExternos.ESPERA).until(() -> status.equals(statusOs(osId)));
	}

	public String statusOs(long osId) {
		return jdbcTemplate.queryForObject("SELECT status FROM ordem_servico WHERE id = ?", String.class, osId);
	}

	/** Etapa e situação da Saga da OS. */
	public Map<String, Object> saga(long osId) {
		return jdbcTemplate.queryForMap(
				"SELECT etapa, situacao, motivo, tentativas FROM saga_ordem_servico WHERE ordem_servico_id = ?", osId);
	}

	public void aguardarSituacaoSaga(long osId, String situacao) {
		await().atMost(SimuladorServicosExternos.ESPERA).until(() -> situacao.equals(saga(osId).get("situacao")));
	}

	/** Vence o prazo da Saga. Usa o horário da JVM, o mesmo usado para gravar e comparar o prazo. */
	public void vencerPrazo(long osId) {
		jdbcTemplate.update("UPDATE saga_ordem_servico SET prazo = ? WHERE ordem_servico_id = ?",
				LocalDateTime.now().minusMinutes(1), osId);
	}
}
