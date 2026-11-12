package br.com.oficina.os.security;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.oficina.os.cliente.application.dto.ClienteResponse;
import br.com.oficina.os.cliente.application.usecase.ClienteUseCase;
import br.com.oficina.os.config.ControllerIntegrationTestSupport;
import br.com.oficina.os.config.IntegrationTest;
import br.com.oficina.os.ordemservico.application.usecase.OrdemServicoUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Garante que o token emitido pela function serverless de autenticação por CPF é reconhecido (antes
 * da Fase 3 o filtro procurava o subject na tabela {@code usuario} e descartava o token) e que ele
 * não dá acesso aos cadastros da oficina: o cliente só acessa as próprias OS (ver
 * OrdemServicoFluxoIntegrationTest#clienteSoAcessaAsPropriasOrdensDeServico).
 */
@IntegrationTest
class TokenClienteControllerTest extends ControllerIntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClienteUseCase clienteUseCase;

    @MockitoBean
    private OrdemServicoUseCase ordemServicoUseCase;

    @Test
    void tokenDeClienteDeveSerReconhecidoNasRotasDeOrdemServico() throws Exception {
        when(ordemServicoUseCase.listarPorCliente(7L)).thenReturn(List.of());

        mockMvc.perform(get("/ordens/cliente/{id}", 7L)
                        .header("Authorization", bearerTokenCliente(7L, "52998224725", "Ana Souza")))
                .andExpect(status().isOk());
    }

    @Test
    void tokenDeClienteNaoDeveAcessarCadastroDeClientes() throws Exception {
        when(clienteUseCase.listar()).thenReturn(List.of(criarCliente()));

        mockMvc.perform(get("/clientes")
                        .header("Authorization", bearerTokenCliente(7L, "52998224725", "Ana Souza")))
                .andExpect(status().isForbidden());

        verify(clienteUseCase, never()).listar();
    }

    @Test
    void deveRecusarRotaProtegidaSemToken() throws Exception {
        mockMvc.perform(get("/clientes"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void deveRecusarTokenDeClienteAssinadoComOutroSegredo() throws Exception {
        mockMvc.perform(get("/clientes")
                        .header("Authorization", bearerTokenClienteComOutroSegredo(7L, "52998224725")))
                .andExpect(status().is4xxClientError());
    }

    private ClienteResponse criarCliente() {
        return new ClienteResponse(
                7L,
                "Ana Souza",
                "52998224725",
                "11999999999",
                "ana@email.com",
                "01001000",
                "Praca da Se",
                "Se",
                "Sao Paulo",
                "SP",
                LocalDate.of(1990, 1, 1),
                true,
                LocalDateTime.now(),
                null
        );
    }
}
