package br.com.oficina.os.ordemservico.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.oficina.os.auth.gestaousuarios.domain.RoleUsuario;
import br.com.oficina.os.auth.gestaousuarios.domain.Usuario;
import br.com.oficina.os.config.ControllerIntegrationTestSupport;
import br.com.oficina.os.config.IntegrationTest;
import br.com.oficina.os.security.JwtService;
import br.com.oficina.os.security.domain.SecurityUserDetails;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Fluxo de atendimento ponta a ponta, sem mocks: HTTP, segurança, regras, JPA, migrations, trigger de
 * código e PostgreSQL real (Testcontainers). Vai da abertura da OS até o pedido de orçamento, que é
 * onde a responsabilidade passa para a Saga.
 */
@IntegrationTest
class OrdemServicoFluxoIntegrationTest extends ControllerIntegrationTestSupport {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private final ObjectMapper objectMapper = new ObjectMapper();

	private String token;

	@BeforeEach
	void setUp() {
		token = bearerTokenAdmin(jwtService);
	}

	@Test
	void deveAbrirDiagnosticarESolicitarOrcamentoDeUmaOs() throws Exception {
		long clienteId = criarCliente();
		long veiculoId = criarVeiculo(clienteId);
		long servicoId = novoIdDoCatalogo();
		long itemId = novoIdDoCatalogo();

		JsonNode os = abrirOs(clienteId, veiculoId, servicoId, itemId);
		long osId = os.get("id").asLong();
		String codigo = os.get("codigo").asText();

		diagnosticar(osId, clienteId, veiculoId, servicoId, itemId);

		// Fim do diagnóstico: o pedido vai ao Billing e a OS segue em diagnóstico (a espera é da Saga)
		executar(patch("/ordens/{id}/solicitar-aprovacao", osId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("EM_DIAGNOSTICO"))
				.andExpect(jsonPath("$.dataSolicitacaoOrcamento").isNotEmpty());

		// Entregar antes da execução é transição inválida
		executar(patch("/ordens/{id}/entregar", osId))
				.andExpect(status().isUnprocessableContent());

		// Depois do pedido de orçamento os dados da OS não mudam mais
		executar(put("/ordens/{id}", osId), Map.of(
				"descricaoServicosExecutados", "Outra descrição",
				"clienteId", clienteId,
				"veiculoId", veiculoId,
				"servicos", List.of(servico(servicoId))
		)).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.detail").value(
						"Não é possível alterar a ordem de serviço enquanto o orçamento está sendo gerado"));

		assertConsultas(osId, codigo, clienteId);
		assertHistoricoComOrigem(osId);
		assertHistoricoPelaApi(osId);
		assertClienteEVeiculoEmUsoNaoPodemSerInativados(clienteId, veiculoId);
	}

	/** Abertura: RECEBIDA, código gerado pelo trigger e sem valor em reais. */
	private JsonNode abrirOs(long clienteId, long veiculoId, long servicoId, long itemId) throws Exception {
		JsonNode os = json(executar(post("/ordens"), Map.of(
				"descricaoProblema", "Barulho no motor",
				"descricaoServicosExecutados", "Revisão",
				"clienteId", clienteId,
				"veiculoId", veiculoId,
				"servicos", List.of(servico(servicoId)),
				"itens", List.of(Map.of("itemId", itemId, "nome", "Filtro de óleo", "quantidade", 2))
		)).andExpect(status().isCreated()));

		assertThat(os.get("codigo").asText()).matches("OS-\\d{4}-\\d{6}");
		assertThat(os.get("status").asText()).isEqualTo("RECEBIDA");
		assertThat(os.has("valorTotalItens") || os.has("valorTotalServicos")).as("a OS não guarda valor").isFalse();
		return os;
	}

	private void diagnosticar(long osId, long clienteId, long veiculoId, long servicoId, long itemId)
			throws Exception {
		long outroServicoId = novoIdDoCatalogo();

		// Fora do diagnóstico não aceita itens
		executar(post("/ordens/{id}/itens", osId), Map.of("itemId", itemId, "nome", "Filtro de óleo", "quantidade", 1))
				.andExpect(status().isUnprocessableContent());

		executar(patch("/ordens/{id}/iniciar-diagnostico", osId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("EM_DIAGNOSTICO"));

		executar(post("/ordens/{id}/itens", osId), Map.of("itemId", itemId, "nome", "Filtro de óleo", "quantidade", 1))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.valorTotalItens").doesNotExist());
		executar(post("/ordens/{id}/servicos", osId), Map.of("servicoId", outroServicoId, "nome", "Alinhamento"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.valorTotalServicos").doesNotExist());

		executar(get("/ordens/{id}/itens", osId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.itens.length()").value(2));
		executar(get("/ordens/{id}/servicos", osId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.servicos.length()").value(2));

		executar(put("/ordens/{id}", osId), Map.of(
				"descricaoProblema", "Barulho no motor ao ligar",
				"descricaoServicosExecutados", "Revisão completa",
				"clienteId", clienteId,
				"veiculoId", veiculoId,
				"servicos", List.of(servico(servicoId))
		)).andExpect(status().isOk())
				.andExpect(jsonPath("$.descricaoProblema").value("Barulho no motor ao ligar"));
	}

	private void assertConsultas(long osId, String codigo, long clienteId) throws Exception {
		executar(get("/ordens/{id}", osId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.codigo").value(codigo))
				.andExpect(jsonPath("$.descricaoProblema").value("Barulho no motor ao ligar"));
		executar(get("/ordens/{id}/status", osId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.codigo").value(codigo))
				.andExpect(jsonPath("$.status").value("EM_DIAGNOSTICO"));
		executar(get("/ordens"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.id == " + osId + ")]").exists());
		executar(get("/ordens/cliente/{id}", clienteId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));
		executar(get("/ordens/codigo/{codigo}", codigo))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(osId));
		executar(get("/ordens/status/{status}", "EM_DIAGNOSTICO"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.id == " + osId + ")]").exists());
		mockMvc.perform(get("/public/ordens/codigo/{codigo}", codigo))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("EM_DIAGNOSTICO"));
	}

	/** Histórico gravado no banco, com a origem de cada mudança. */
	private void assertHistoricoComOrigem(long osId) {
		List<Map<String, Object>> historico = jdbcTemplate.queryForList(
				"select status, origem from historico_ordem_servico where ordem_servico_id = ? order by id", osId);
		assertThat(historico).extracting(linha -> linha.get("status"))
				.containsExactly("RECEBIDA", "EM_DIAGNOSTICO");
		assertThat(historico).extracting(linha -> linha.get("origem")).containsOnly("API");
	}

	private void assertHistoricoPelaApi(long osId) throws Exception {
		executar(get("/ordens/{id}/historico", osId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].status").value("RECEBIDA"))
				.andExpect(jsonPath("$[1].status").value("EM_DIAGNOSTICO"))
				.andExpect(jsonPath("$[1].descricao").value("Em diagnóstico"))
				.andExpect(jsonPath("$[*].origem").value(Matchers.everyItem(
						Matchers.is("API"))))
				.andExpect(jsonPath("$[0].data").isNotEmpty());
	}

	@Test
	void deveCancelarOsELiberarOsCadastros() throws Exception {
		long clienteId = criarCliente();
		long veiculoId = criarVeiculo(clienteId);
		long servicoId = novoIdDoCatalogo();
		long osId = json(executar(post("/ordens"), Map.of(
				"descricaoServicosExecutados", "Freios",
				"clienteId", clienteId,
				"veiculoId", veiculoId,
				"servicos", List.of(servico(servicoId))
		)).andExpect(status().isCreated())).get("id").asLong();

		executar(patch("/ordens/{id}/iniciar-diagnostico", osId)).andExpect(status().isOk());

		executar(patch("/ordens/{id}/cancelar", osId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELADA"))
				.andExpect(jsonPath("$.dataCancelada").isNotEmpty());

		// Cancelar de novo, ou seguir o fluxo, é transição inválida
		executar(patch("/ordens/{id}/cancelar", osId)).andExpect(status().isUnprocessableContent());
		executar(patch("/ordens/{id}/solicitar-aprovacao", osId)).andExpect(status().isUnprocessableContent());

		executar(get("/ordens/{id}/historico", osId))
				.andExpect(jsonPath("$[*].status").value(Matchers.contains(
						"RECEBIDA", "EM_DIAGNOSTICO", "CANCELADA")));

		// OS cancelada não está ativa: o veículo pode ser inativado
		executar(delete("/veiculos/{id}", veiculoId)).andExpect(status().isNoContent());
	}

	@Test
	void osPagaSemSagaNaoPodeSerCanceladaSemIntervencaoManual() throws Exception {
		long clienteId = criarCliente();
		long veiculoId = criarVeiculo(clienteId);
		long servicoId = novoIdDoCatalogo();
		long osId = json(executar(post("/ordens"), Map.of(
				"descricaoServicosExecutados", "Suspensão",
				"clienteId", clienteId,
				"veiculoId", veiculoId,
				"servicos", List.of(servico(servicoId))
		))).get("id").asLong();
		// Estado inconsistente de propósito: OS paga sem Saga (o caminho normal está no SagaIntegrationTest)
		jdbcTemplate.update(
				"update ordem_servico set status = 'APROVADA', pagamento_id = 55 where id = ?", osId);

		executar(patch("/ordens/{id}/cancelar", osId))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.detail").value(Matchers.containsString("intervenção manual")));

		assertThat(jdbcTemplate.queryForObject("select status from ordem_servico where id = ?", String.class, osId))
				.isEqualTo("APROVADA");
	}

	/**
	 * Token de cliente (function de autenticação por CPF): só enxerga e cancela as próprias OS e não
	 * acessa cadastros nem ações da oficina.
	 */
	@Test
	void clienteSoAcessaAsPropriasOrdensDeServico() throws Exception {
		long clienteId = criarCliente();
		long outroClienteId = criarCliente();
		long servicoId = novoIdDoCatalogo();
		long minhaOs = abrirOsSimples(clienteId, servicoId);
		long osDoOutro = abrirOsSimples(outroClienteId, servicoId);
		String tokenCliente = bearerTokenCliente(clienteId, "52998224725", "Cliente Teste");

		comToken(get("/ordens/{id}", minhaOs), tokenCliente).andExpect(status().isOk());
		comToken(get("/ordens/{id}/status", minhaOs), tokenCliente).andExpect(status().isOk());
		comToken(get("/ordens/{id}/historico", minhaOs), tokenCliente).andExpect(status().isOk());
		comToken(get("/ordens/cliente/{id}", clienteId), tokenCliente)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));

		// OS de outro cliente responde como inexistente, para não revelar que existe
		comToken(get("/ordens/{id}", osDoOutro), tokenCliente).andExpect(status().isNotFound());
		comToken(get("/ordens/{id}/status", osDoOutro), tokenCliente).andExpect(status().isNotFound());
		comToken(get("/ordens/{id}/historico", osDoOutro), tokenCliente).andExpect(status().isNotFound());
		comToken(patch("/ordens/{id}/cancelar", osDoOutro), tokenCliente).andExpect(status().isNotFound());
		comToken(get("/ordens/cliente/{id}", outroClienteId), tokenCliente)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.title").value("Acesso negado"));
		assertThat(jdbcTemplate.queryForObject("select status from ordem_servico where id = ?", String.class, osDoOutro))
				.isEqualTo("RECEBIDA");

		// Cadastros, listagens gerais e ações da oficina são só de operadores
		comToken(get("/ordens"), tokenCliente).andExpect(status().isForbidden());
		comToken(get("/clientes"), tokenCliente).andExpect(status().isForbidden());
		comToken(patch("/ordens/{id}/iniciar-diagnostico", minhaOs), tokenCliente).andExpect(status().isForbidden());
		comToken(post("/usuarios"), tokenCliente).andExpect(status().isForbidden());

		comToken(patch("/ordens/{id}/cancelar", minhaOs), tokenCliente)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELADA"));
	}

	@Test
	void somenteAdminCadastraUsuarios() throws Exception {
		String email = "atendente." + ThreadLocalRandom.current().nextInt(1_000_000) + "@oficina.com";
		executar(post("/usuarios"), Map.of(
				"nome", "Atendente", "email", email, "senha", "senha123", "role", "ATENDENTE"
		)).andExpect(status().isCreated());
		String tokenAtendente = bearerTokenOperador(email, RoleUsuario.ATENDENTE);

		mockMvc.perform(post("/usuarios")
						.header("Authorization", tokenAtendente)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"nome", "Outro", "email", "outro." + email, "senha", "senha123", "role", "ADMIN"))))
				.andExpect(status().isForbidden());

		// O atendente opera normalmente o restante da API
		comToken(get("/ordens"), tokenAtendente).andExpect(status().isOk());
	}

	private long abrirOsSimples(long clienteId, long servicoId) throws Exception {
		long veiculoId = criarVeiculo(clienteId);
		return json(executar(post("/ordens"), Map.of(
				"descricaoServicosExecutados", "Serviço",
				"clienteId", clienteId,
				"veiculoId", veiculoId,
				"servicos", List.of(servico(servicoId))
		)).andExpect(status().isCreated())).get("id").asLong();
	}

	private ResultActions comToken(MockHttpServletRequestBuilder requisicao, String bearer) throws Exception {
		return mockMvc.perform(requisicao.header("Authorization", bearer));
	}

	private String bearerTokenOperador(String email, RoleUsuario role) {
		Usuario usuario = new Usuario();
		usuario.setEmail(email);
		usuario.setNome("Operador");
		usuario.setSenha("nao-usada");
		usuario.setRole(role);
		usuario.setAtivo(true);
		return "Bearer " + jwtService.gerarToken(new SecurityUserDetails(usuario));
	}

	private void assertClienteEVeiculoEmUsoNaoPodemSerInativados(long clienteId, long veiculoId) throws Exception {
		executar(delete("/veiculos/{id}", veiculoId)).andExpect(status().isUnprocessableContent());
		executar(delete("/clientes/{id}", clienteId)).andExpect(status().isUnprocessableContent());
	}

	@Test
	void naoDeveAbrirOsComVeiculoDeOutroCliente() throws Exception {
		long clienteId = criarCliente();
		long outroClienteId = criarCliente();
		long veiculoDoOutro = criarVeiculo(outroClienteId);
		long servicoId = novoIdDoCatalogo();

		executar(post("/ordens"), Map.of(
				"descricaoServicosExecutados", "Revisão",
				"clienteId", clienteId,
				"veiculoId", veiculoDoOutro,
				"servicos", List.of(servico(servicoId))
		)).andExpect(status().isUnprocessableContent());

		Integer total = jdbcTemplate.queryForObject(
				"select count(*) from ordem_servico where cliente_id = ?", Integer.class, clienteId);
		assertThat(total).isZero();
	}

	@Test
	void codigoDaOsDeveSerSequencialNoAno() throws Exception {
		long clienteId = criarCliente();
		long veiculoId = criarVeiculo(clienteId);
		long servicoId = novoIdDoCatalogo();
		Map<String, Object> corpo = Map.of(
				"descricaoServicosExecutados", "Diagnóstico",
				"clienteId", clienteId,
				"veiculoId", veiculoId,
				"servicos", List.of(servico(servicoId))
		);

		String primeiro = json(executar(post("/ordens"), corpo)).get("codigo").asText();
		String segundo = json(executar(post("/ordens"), corpo)).get("codigo").asText();

		assertThat(sequencial(segundo)).isEqualTo(sequencial(primeiro) + 1);
	}

	@Test
	void indiceDeCpfDaLambdaDeveExistir() {
		Integer indices = jdbcTemplate.queryForObject(
				"select count(*) from pg_indexes where indexname = 'idx_cliente_cpf_cnpj_digitos'", Integer.class);
		assertThat(indices).isEqualTo(1);
	}

	private long criarCliente() throws Exception {
		return json(executar(post("/clientes"), Map.of(
				"nome", "Cliente Teste",
				"cpfCnpj", gerarCpf(),
				"email", "cliente@teste.com",
				"cep", "01001000",
				"logradouro", "Praça da Sé",
				"bairro", "Sé",
				"cidade", "São Paulo",
				"uf", "SP",
				"data_nascimento", "1990-05-10"
		)).andExpect(status().isCreated())).get("id").asLong();
	}

	private long criarVeiculo(long clienteId) throws Exception {
		return json(executar(post("/veiculos"), Map.of(
				"clienteId", clienteId,
				"placa", gerarPlaca(),
				"marca", "Fiat",
				"modelo", "Argo",
				"ano", 2022
		)).andExpect(status().isCreated())).get("id").asLong();
	}

	/** O catálogo é do Billing: o OS só recebe o id e o nome, sem validar nada contra um cadastro local. */
	private long novoIdDoCatalogo() {
		return ThreadLocalRandom.current().nextLong(1, 1_000_000);
	}

	private Map<String, Object> servico(long servicoId) {
		return Map.of("servicoId", servicoId, "nome", "Serviço " + servicoId);
	}

	private ResultActions executar(MockHttpServletRequestBuilder requisicao) throws Exception {
		return mockMvc.perform(requisicao.header("Authorization", token));
	}

	private ResultActions executar(MockHttpServletRequestBuilder requisicao, Object corpo) throws Exception {
		return mockMvc.perform(requisicao
				.header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(corpo)));
	}

	private JsonNode json(ResultActions resultado) throws Exception {
		return objectMapper.readTree(resultado.andReturn().getResponse().getContentAsString());
	}

	private static int sequencial(String codigo) {
		return Integer.parseInt(codigo.substring(codigo.lastIndexOf('-') + 1));
	}

	/** CPF válido e aleatório, para que os testes não colidam na restrição de unicidade. */
	private static String gerarCpf() {
		int[] digitos = new int[11];
		for (int i = 0; i < 9; i++) {
			digitos[i] = ThreadLocalRandom.current().nextInt(10);
		}
		digitos[0] = 1 + ThreadLocalRandom.current().nextInt(9);
		digitos[9] = digitoVerificador(digitos, 9);
		digitos[10] = digitoVerificador(digitos, 10);
		StringBuilder cpf = new StringBuilder();
		for (int digito : digitos) {
			cpf.append(digito);
		}
		return cpf.toString();
	}

	private static int digitoVerificador(int[] digitos, int tamanho) {
		int soma = 0;
		for (int i = 0; i < tamanho; i++) {
			soma += digitos[i] * (tamanho + 1 - i);
		}
		int resto = (soma * 10) % 11;
		return resto == 10 ? 0 : resto;
	}

	/** Placa aleatória no padrão Mercosul (AAA0A00). */
	private static String gerarPlaca() {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		String letras = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
		return "" + letras.charAt(random.nextInt(26)) + letras.charAt(random.nextInt(26))
				+ letras.charAt(random.nextInt(26)) + random.nextInt(10) + letras.charAt(random.nextInt(26))
				+ random.nextInt(10) + random.nextInt(10);
	}
}
