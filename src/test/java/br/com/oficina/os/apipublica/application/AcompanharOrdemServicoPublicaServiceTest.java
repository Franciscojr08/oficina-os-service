package br.com.oficina.os.apipublica.application;

import static br.com.oficina.os.shared.exception.ExceptionMessage.SERVICE_ORDER_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import br.com.oficina.os.apipublica.AcompanharOrdemServicoPublicaResponse;
import br.com.oficina.os.apipublica.AcompanharOrdemServicoPublicaService;
import br.com.oficina.os.ordemservico.application.StatusOrdemServico;
import br.com.oficina.os.ordemservico.application.gateway.OrdemServicoGateway;
import br.com.oficina.os.ordemservico.domain.OrdemServico;
import br.com.oficina.os.shared.exception.RecursoNaoEncontradoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class AcompanharOrdemServicoPublicaServiceTest {

    @Mock
    private OrdemServicoGateway ordemServicoGateway;

    @InjectMocks
    private AcompanharOrdemServicoPublicaService service;

    @Test
    void deveAcompanharOrdemServicoPorCodigo() {
        LocalDateTime dataCadastro = LocalDateTime.of(2026, 5, 1, 10, 0);
        OrdemServico ordemServico = new OrdemServico();
        ordemServico.setCodigo("OS-2026-0001");
        ordemServico.setDescricaoProblema("Barulho no motor");
        ordemServico.setStatus(StatusOrdemServico.EM_EXECUCAO);
        ordemServico.setDataCadastro(dataCadastro);

        when(ordemServicoGateway.findByCodigo("OS-2026-0001")).thenReturn(Optional.of(ordemServico));

        AcompanharOrdemServicoPublicaResponse response = service.acompanharPorCodigo("OS-2026-0001");

        assertEquals("OS-2026-0001", response.codigo());
        assertEquals("Barulho no motor", response.descricaoProblema());
        assertEquals(StatusOrdemServico.EM_EXECUCAO, response.status());
        assertEquals(dataCadastro, response.dataCadastro());
    }

    @Test
    void deveLancarExcecaoQuandoCodigoNaoExistir() {
        when(ordemServicoGateway.findByCodigo("OS-404")).thenReturn(Optional.empty());

        RecursoNaoEncontradoException exception = assertThrows(
                RecursoNaoEncontradoException.class,
                () -> service.acompanharPorCodigo("OS-404")
        );

        assertEquals(SERVICE_ORDER_NOT_FOUND, exception.getMessage());
    }
}
