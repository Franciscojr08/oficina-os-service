package br.com.oficina.os.ordemservico.entrypoint.controller;

import static br.com.oficina.os.shared.exception.ExceptionMessage.CANNOT_CANCEL_PAID_SERVICE_ORDER;
import static br.com.oficina.os.shared.exception.ExceptionMessage.SERVICE_ORDER_NOT_FOUND;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.oficina.os.config.ControllerIntegrationTestSupport;
import br.com.oficina.os.config.IntegrationTest;
import br.com.oficina.os.ordemservico.application.StatusOrdemServico;
import br.com.oficina.os.ordemservico.application.dto.HistoricoOrdemServicoResponse;
import br.com.oficina.os.ordemservico.application.dto.OrdemServicoRequest;
import br.com.oficina.os.ordemservico.application.dto.OrdemServicoResponse;
import br.com.oficina.os.ordemservico.application.dto.StatusOrdemServicoResponse;
import br.com.oficina.os.ordemservico.application.usecase.OrdemServicoUseCase;
import br.com.oficina.os.ordemservico.itens.application.dto.ItemOrdemServicoRequest;
import br.com.oficina.os.ordemservico.itens.application.dto.ItemOrdemServicoResponse;
import br.com.oficina.os.ordemservico.itens.application.dto.ListaItensOrdemServicoResponse;
import br.com.oficina.os.ordemservico.itens.application.usecase.ItemOrdemServicoUseCase;
import br.com.oficina.os.ordemservico.servicos.application.dto.ListaServicosOrdemServicoResponse;
import br.com.oficina.os.ordemservico.servicos.application.dto.ServicoOrdemServicoRequest;
import br.com.oficina.os.ordemservico.servicos.application.dto.ServicoOrdemServicoResponse;
import br.com.oficina.os.ordemservico.servicos.application.usecase.ServicoOrdemServicoUseCase;
import br.com.oficina.os.security.JwtService;
import br.com.oficina.os.shared.exception.RecursoNaoEncontradoException;
import br.com.oficina.os.shared.exception.RegraNegocioException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

@IntegrationTest
class OrdemServicoControllerTest extends ControllerIntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private OrdemServicoUseCase ordemServicoService;

    @MockitoBean
    private ItemOrdemServicoUseCase itemOrdemServicoService;

    @MockitoBean
    private ServicoOrdemServicoUseCase servicoOrdemServicoService;

    @Test
    void deveListarOrdensServico() throws Exception {
        when(ordemServicoService.listar()).thenReturn(List.of(criarOrdemServico()));

        mockMvc.perform(get("/ordens")
                        .header("Authorization", bearerTokenAdmin(jwtService)))
                .andDo(print())
                .andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(1L))
				.andExpect(jsonPath("$[0].codigo").value("Codigo"))
				.andExpect(jsonPath("$[0].status").value(StatusOrdemServico.RECEBIDA.name()));
    }

    @Test
    void deveListarOrdensServicoPorCliente() throws Exception {
        when(ordemServicoService.listarPorCliente(1L)).thenReturn(List.of(criarOrdemServico()));

        mockMvc.perform(get("/ordens/cliente/{id}", 1L)
                        .header("Authorization", bearerTokenAdmin(jwtService)))
                .andDo(print())
                .andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(1L));
    }

    @Test
    void deveListarOrdensServicoPorCodigo() throws Exception {
        when(ordemServicoService.listarPorCodigo("codigo")).thenReturn(List.of(criarOrdemServico()));

        mockMvc.perform(get("/ordens/codigo/{codigo}", "codigo")
                        .header("Authorization", bearerTokenAdmin(jwtService)))
                .andDo(print())
                .andExpect(status().isOk())
				.andExpect(jsonPath("$[0].codigo").value("Codigo"));
    }

    @Test
    void deveListarOrdensServicoPorStatus() throws Exception {
        when(ordemServicoService.listarPorStatus(StatusOrdemServico.ENTREGUE))
                .thenReturn(List.of(criarOrdemServico()));

        mockMvc.perform(get("/ordens/status/{status}", StatusOrdemServico.ENTREGUE)
                        .header("Authorization", bearerTokenAdmin(jwtService)))
                .andDo(print())
                .andExpect(status().isOk())
				.andExpect(jsonPath("$[0].status").value(StatusOrdemServico.RECEBIDA.name()));
    }

    @Test
    void deveCriarOrdemServicoComSucesso() throws Exception {
        OrdemServicoRequest request = criarOrdemServicoRequest();

        when(ordemServicoService.criar(request)).thenReturn(criarOrdemServico());

        mockMvc.perform(post("/ordens")
                        .header("Authorization", bearerTokenAdmin(jwtService))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(1L))
				.andExpect(jsonPath("$.codigo").value("Codigo"))
				.andExpect(jsonPath("$.status").value(StatusOrdemServico.RECEBIDA.name()));
    }

    @Test
    void naoDeveCriarOrdemServicoQuandoServicosEstiverVazio() throws Exception {
		OrdemServicoRequest request = new OrdemServicoRequest(
				"Descricao",
				"Observacoes",
				"Descricao",
				1L,
				2L,
				List.of(),
				List.of()
		);

		mockMvc.perform(post("/ordens")
						.header("Authorization", bearerTokenAdmin(jwtService))
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andDo(print())
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("servicos: É necessário informar pelo menos um serviço"));

		verify(ordemServicoService, never()).criar(any(OrdemServicoRequest.class));
	}

    @Test
    void deveAtualizarOrdemServicoComSucesso() throws Exception {
        OrdemServicoRequest request = criarOrdemServicoRequest();

        when(ordemServicoService.atualizar(1L, request))
                .thenReturn(criarOrdemServico());

        mockMvc.perform(put("/ordens/{id}", 1L)
                        .with(csrf())
                        .header("Authorization", bearerTokenAdmin(jwtService))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(1L))
				.andExpect(jsonPath("$.codigo").value("Codigo"));
    }

    @Test
    void deveAdicionarItemNaOrdemServico() throws Exception {
        ItemOrdemServicoRequest request = criarItemOrdemServicoRequest();

		when(itemOrdemServicoService.adicionarItem(1L, request))
				.thenReturn(criarListaItensOrdemServicoResponse());

        mockMvc.perform(post("/ordens/{id}/itens", 1L)
                        .with(csrf())
                        .header("Authorization", bearerTokenAdmin(jwtService))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
				.andExpect(jsonPath("$.itens[0].itemId").value(1L))
				.andExpect(jsonPath("$.valorTotalItens").doesNotExist());
    }

    @Test
    void naoDeveAdicionarItemQuandoQuantidadeNaoForPositiva() throws Exception {
		ItemOrdemServicoRequest request = new ItemOrdemServicoRequest(1L, "Filtro", 0);

		mockMvc.perform(post("/ordens/{id}/itens", 1L)
						.with(csrf())
						.header("Authorization", bearerTokenAdmin(jwtService))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andDo(print())
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("quantidade: Quantidade deve ser maior que zero"));

		verify(itemOrdemServicoService, never()).adicionarItem(1L, request);
	}

    @Test
    void deveListarItensPorOrdemServico() throws Exception {
        when(itemOrdemServicoService.listarItensPorOrdemServico(1L))
                .thenReturn(criarListaItensOrdemServicoResponse());

        mockMvc.perform(get("/ordens/{id}/itens", 1L)
                        .header("Authorization", bearerTokenAdmin(jwtService)))
                .andDo(print())
                .andExpect(status().isOk())
				.andExpect(jsonPath("$.itens[0].itemId").value(1L))
				.andExpect(jsonPath("$.valorTotalItens").doesNotExist());
    }

    @Test
    void deveAdicionarServicoNaOrdemServico() throws Exception {
        ServicoOrdemServicoRequest request = criarServicoOrdemServicoRequest();

		when(servicoOrdemServicoService.adicionarServico(1L, request))
				.thenReturn(criarListaServicosOrdemServicoResponse());

        mockMvc.perform(post("/ordens/{id}/servicos", 1L)
                        .with(csrf())
                        .header("Authorization", bearerTokenAdmin(jwtService))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
				.andExpect(jsonPath("$.servicos[0].servicoId").value(1L))
				.andExpect(jsonPath("$.valorTotalServicos").doesNotExist());
    }

    @Test
    void deveListarServicosPorOrdemServico() throws Exception {
        when(servicoOrdemServicoService.listarServicosPorOrdemServico(1L))
                .thenReturn(criarListaServicosOrdemServicoResponse());

        mockMvc.perform(get("/ordens/{id}/servicos", 1L)
                        .header("Authorization", bearerTokenAdmin(jwtService)))
                .andDo(print())
                .andExpect(status().isOk())
				.andExpect(jsonPath("$.servicos[0].servicoId").value(1L))
				.andExpect(jsonPath("$.valorTotalServicos").doesNotExist());
    }

    @Test
    void deveIniciarDiagnosticoDaOrdemServico() throws Exception {
        when(ordemServicoService.iniciarDiagnostico(1L)).thenReturn(criarOrdemServico());

        mockMvc.perform(patch("/ordens/{id}/iniciar-diagnostico", 1L)
                        .with(csrf())
                        .header("Authorization", bearerTokenAdmin(jwtService)))
                .andDo(print())
                .andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(1L));
    }

    @Test
    void deveSolicitarAprovacaoDaOrdemServico() throws Exception {
        when(ordemServicoService.solicitarAprovacao(1L)).thenReturn(criarOrdemServico());

        mockMvc.perform(patch("/ordens/{id}/solicitar-aprovacao", 1L)
                        .with(csrf())
                        .header("Authorization", bearerTokenAdmin(jwtService)))
                .andDo(print())
                .andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(1L));
    }

    @Test
    void deveEntregarOrdemServico() throws Exception {
        when(ordemServicoService.entregar(1L)).thenReturn(criarOrdemServico());

        mockMvc.perform(patch("/ordens/{id}/entregar", 1L)
                        .with(csrf())
                        .header("Authorization", bearerTokenAdmin(jwtService)))
                .andDo(print())
                .andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(1L));
    }

    @Test
    void deveBuscarOrdemServicoPorId() throws Exception {
        when(ordemServicoService.buscarPorId(1L)).thenReturn(criarOrdemServico());

        mockMvc.perform(get("/ordens/{id}", 1L)
                        .header("Authorization", bearerTokenAdmin(jwtService)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.codigo").value("Codigo"));
    }

    @Test
    void buscarPorIdDeveResponder404EmProblemJsonQuandoNaoExiste() throws Exception {
        when(ordemServicoService.buscarPorId(99L))
                .thenThrow(new RecursoNaoEncontradoException(SERVICE_ORDER_NOT_FOUND));

        mockMvc.perform(get("/ordens/{id}", 99L)
                        .header("Authorization", bearerTokenAdmin(jwtService)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(SERVICE_ORDER_NOT_FOUND));
    }

    @Test
    void deveCancelarOrdemServico() throws Exception {
        when(ordemServicoService.cancelar(1L)).thenReturn(criarOrdemServico(StatusOrdemServico.CANCELADA));

        mockMvc.perform(patch("/ordens/{id}/cancelar", 1L)
                        .with(csrf())
                        .header("Authorization", bearerTokenAdmin(jwtService)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(StatusOrdemServico.CANCELADA.name()));
    }

    @Test
    void cancelarDeveResponder422QuandoRegraImpede() throws Exception {
        when(ordemServicoService.cancelar(1L))
                .thenThrow(new RegraNegocioException(CANNOT_CANCEL_PAID_SERVICE_ORDER));

        mockMvc.perform(patch("/ordens/{id}/cancelar", 1L)
                        .with(csrf())
                        .header("Authorization", bearerTokenAdmin(jwtService)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(CANNOT_CANCEL_PAID_SERVICE_ORDER));
    }

    @Test
    void deveAprovarOrcamento() throws Exception {
        when(ordemServicoService.aprovar(1L)).thenReturn(criarOrdemServico(StatusOrdemServico.AGUARDANDO_APROVACAO));

        mockMvc.perform(patch("/ordens/{id}/aprovar", 1L)
                        .with(csrf())
                        .header("Authorization", bearerTokenAdmin(jwtService)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(StatusOrdemServico.AGUARDANDO_APROVACAO.name()));
    }

    @Test
    void deveReprovarOrcamento() throws Exception {
        when(ordemServicoService.reprovar(1L)).thenReturn(criarOrdemServico(StatusOrdemServico.CANCELADA));

        mockMvc.perform(patch("/ordens/{id}/reprovar", 1L)
                        .with(csrf())
                        .header("Authorization", bearerTokenAdmin(jwtService)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(StatusOrdemServico.CANCELADA.name()));
    }

    @Test
    void aprovarEReprovarDevemResponder422QuandoAOsNaoEstaAguardandoADecisao() throws Exception {
        when(ordemServicoService.aprovar(1L)).thenThrow(new RegraNegocioException("fora da etapa"));
        when(ordemServicoService.reprovar(1L)).thenThrow(new RegraNegocioException("fora da etapa"));

        for (String acao : List.of("aprovar", "reprovar")) {
            mockMvc.perform(patch("/ordens/{id}/" + acao, 1L)
                            .with(csrf())
                            .header("Authorization", bearerTokenAdmin(jwtService)))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.detail").value("fora da etapa"));
        }
    }

    @Test
    void oClienteDecideSobreOProprioOrcamentoEAprovacaoExigeAutenticacao() throws Exception {
        when(ordemServicoService.aprovar(1L)).thenReturn(criarOrdemServico(StatusOrdemServico.AGUARDANDO_APROVACAO));
        mockMvc.perform(patch("/ordens/{id}/aprovar", 1L)
                        .with(csrf())
                        .header("Authorization", bearerTokenCliente(7L, "52998224725", "Cliente Teste")))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/ordens/{id}/aprovar", 1L).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/ordens/{id}/reprovar", 1L).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancelarExigeAutenticacao() throws Exception {
        mockMvc.perform(patch("/ordens/{id}/cancelar", 1L).with(csrf()))
                .andExpect(status().isForbidden());

        verify(ordemServicoService, never()).cancelar(any(Long.class));
    }

    @Test
    void deveConsultarHistoricoDaOrdemServico() throws Exception {
        LocalDateTime agora = LocalDateTime.now();
        when(ordemServicoService.consultarHistorico(1L)).thenReturn(List.of(
                new HistoricoOrdemServicoResponse(StatusOrdemServico.RECEBIDA, "Recebida", "API", agora),
                new HistoricoOrdemServicoResponse(
                        StatusOrdemServico.AGUARDANDO_APROVACAO, "Aguardando aprovação", "OrcamentoGerado", agora)
        ));

        mockMvc.perform(get("/ordens/{id}/historico", 1L)
                        .header("Authorization", bearerTokenAdmin(jwtService)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("RECEBIDA"))
                .andExpect(jsonPath("$[0].origem").value("API"))
                .andExpect(jsonPath("$[1].status").value("AGUARDANDO_APROVACAO"))
                .andExpect(jsonPath("$[1].descricao").value("Aguardando aprovação"))
                .andExpect(jsonPath("$[1].origem").value("OrcamentoGerado"))
                .andExpect(jsonPath("$[1].data").exists());
    }

    @Test
    void historicoDeveResponder404QuandoOrdemNaoExiste() throws Exception {
        when(ordemServicoService.consultarHistorico(99L))
                .thenThrow(new RecursoNaoEncontradoException(SERVICE_ORDER_NOT_FOUND));

        mockMvc.perform(get("/ordens/{id}/historico", 99L)
                        .header("Authorization", bearerTokenAdmin(jwtService)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deveConsultarStatusDaOrdemServico() throws Exception {
        when(ordemServicoService.consultarStatus(1L))
                .thenReturn(new StatusOrdemServicoResponse("OS-2026-000001", StatusOrdemServico.EM_DIAGNOSTICO));

        mockMvc.perform(get("/ordens/{id}/status", 1L)
                        .header("Authorization", bearerTokenAdmin(jwtService)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value("OS-2026-000001"))
                .andExpect(jsonPath("$.status").value("EM_DIAGNOSTICO"));
    }

    @Test
    void alterarDeveResponder422QuandoOsJaPediuOrcamento() throws Exception {
        OrdemServicoRequest request = criarOrdemServicoRequest();
        when(ordemServicoService.atualizar(1L, request)).thenThrow(new RegraNegocioException(
                "Não é possível alterar a ordem de serviço enquanto o orçamento está sendo gerado"));

        mockMvc.perform(put("/ordens/{id}", 1L)
                        .with(csrf())
                        .header("Authorization", bearerTokenAdmin(jwtService))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(
                        "Não é possível alterar a ordem de serviço enquanto o orçamento está sendo gerado"));
    }

    @Test
    void rotasQueSairamDoServicoNaoDevemMaisExistir() throws Exception {
        String token = bearerTokenAdmin(jwtService);

        for (String rota : List.of(
                "/ordens/1/iniciar-execucao",
                "/ordens/1/servicos/2/iniciar",
                "/ordens/1/servicos/2/finalizar"
        )) {
            mockMvc.perform(patch(rota).header("Authorization", token))
                    .andExpect(status().isNotFound());
        }
    }

    private OrdemServicoResponse criarOrdemServico() {
        return criarOrdemServico(StatusOrdemServico.RECEBIDA);
    }

    private OrdemServicoResponse criarOrdemServico(StatusOrdemServico status) {
        return new OrdemServicoResponse(
                1L,
                "Codigo",
                "Descricao",
                "Observacoes",
                "Descricao",
                status,
                LocalDateTime.now(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private OrdemServicoRequest criarOrdemServicoRequest() {
		List<ServicoOrdemServicoRequest> servicos = List.of(
				new ServicoOrdemServicoRequest(1L, "Alinhamento"),
				new ServicoOrdemServicoRequest(2L, "Balanceamento")
		);
		List<ItemOrdemServicoRequest> itens = List.of(
				new ItemOrdemServicoRequest(10L, "Filtro de óleo", 2),
				new ItemOrdemServicoRequest(20L, "Pastilha de freio", 1)
		);

        return new OrdemServicoRequest(
                "Descricao",
                "Observacoes",
                "Descricao",
                1L,
                2L,
				servicos,
				itens
        );
    }

    private ItemOrdemServicoRequest criarItemOrdemServicoRequest() {
        return new ItemOrdemServicoRequest(1L, "Filtro de óleo", 10);
    }

    private ServicoOrdemServicoRequest criarServicoOrdemServicoRequest() {
        return new ServicoOrdemServicoRequest(1L, "Alinhamento");
    }

    private ListaItensOrdemServicoResponse criarListaItensOrdemServicoResponse() {
        return new ListaItensOrdemServicoResponse(
                List.of(new ItemOrdemServicoResponse(
                        "CodigoOS",
                        1L,
                        "Item",
                        10
                ))
        );
    }

    private ListaServicosOrdemServicoResponse criarListaServicosOrdemServicoResponse() {
        return new ListaServicosOrdemServicoResponse(
                List.of(criarServicoOrdemServicoResponse())
        );
    }

    private ServicoOrdemServicoResponse criarServicoOrdemServicoResponse() {
        return new ServicoOrdemServicoResponse(
                "codigoOS",
                1L,
                "Servico",
                LocalDateTime.now()
        );
    }
}
