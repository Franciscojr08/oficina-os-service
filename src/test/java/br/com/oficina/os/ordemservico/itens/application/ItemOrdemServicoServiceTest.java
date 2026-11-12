package br.com.oficina.os.ordemservico.itens.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.oficina.os.ordemservico.application.OrdemServicoStatusService;
import br.com.oficina.os.ordemservico.application.StatusOrdemServico;
import br.com.oficina.os.ordemservico.application.gateway.OrdemServicoGateway;
import br.com.oficina.os.ordemservico.domain.OrdemServico;
import br.com.oficina.os.ordemservico.itens.application.dto.ItemOrdemServicoRequest;
import br.com.oficina.os.ordemservico.itens.application.dto.ItemOrdemServicoResponse;
import br.com.oficina.os.ordemservico.itens.application.dto.ListaItensOrdemServicoResponse;
import br.com.oficina.os.ordemservico.itens.application.gateway.ItemOrdemServicoGateway;
import br.com.oficina.os.ordemservico.itens.domain.ItemOrdemServico;
import br.com.oficina.os.shared.exception.RecursoNaoEncontradoException;
import br.com.oficina.os.shared.exception.RegraNegocioException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class ItemOrdemServicoServiceTest {

    @Mock
    private ItemOrdemServicoGateway repository;

    @Mock
    private OrdemServicoGateway ordemServicoGateway;

    @Mock
    private OrdemServicoStatusService statusService;

    @InjectMocks
    private ItemOrdemServicoService service;

    @Test
    void testListerItensPorOrdemServico() {
        ItemOrdemServico item = criarItemServico();
        List<ItemOrdemServico> list = List.of(item);

        when(repository.findByOrdemServicoId(anyLong())).thenReturn(list);

        var out = service.listarItensPorOrdemServico(1L);

        assertThat(out).usingRecursiveAssertion().isEqualTo(criarListaItens());
    }

    @Test
    void deveGuardarOIdEACopiaDoNomeSemConsultarCatalogo() {
        OrdemServico os = ordem();
        when(ordemServicoGateway.findById(1L)).thenReturn(Optional.of(os));

        service.adicionarItem(1L, new ItemOrdemServicoRequest(20L, "  Filtro de óleo ", 2));

        ArgumentCaptor<ItemOrdemServico> captor = ArgumentCaptor.forClass(ItemOrdemServico.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getItemId()).isEqualTo(20L);
        assertThat(captor.getValue().getItemNome()).isEqualTo("Filtro de óleo");
        assertThat(captor.getValue().getQuantidade()).isEqualTo(2);
        assertThat(captor.getValue().getOrdemServico()).isSameAs(os);
    }

    @Test
    void deveAdicionarItemDuranteCadastroSemValidarDiagnostico() {
        service.adicionarItemDuranteCadastro(ordem(), new ItemOrdemServicoRequest(20L, "Filtro", 1));

        verify(statusService, never()).validarEmDiagnostico(any(), anyString());
        verify(repository).save(any(ItemOrdemServico.class));
    }

    @Test
    void naoDeveAdicionarItemForaDoDiagnostico() {
        OrdemServico os = ordem();
        when(ordemServicoGateway.findById(1L)).thenReturn(Optional.of(os));
        doThrow(new RegraNegocioException("fora do diagnóstico")).when(statusService)
                .validarEmDiagnostico(any(), anyString());

        ItemOrdemServicoRequest request = new ItemOrdemServicoRequest(20L, "Filtro", 1);
        assertThatThrownBy(() -> service.adicionarItem(1L, request)).isInstanceOf(RegraNegocioException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void deveFalharQuandoAOrdemNaoExiste() {
        when(ordemServicoGateway.findById(99L)).thenReturn(Optional.empty());

        ItemOrdemServicoRequest request = new ItemOrdemServicoRequest(20L, "Filtro", 1);
        assertThatThrownBy(() -> service.adicionarItem(99L, request))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    private static OrdemServico ordem() {
        OrdemServico os = new OrdemServico();
        os.setId(1L);
        os.setCodigo("OS-1");
        os.setStatus(StatusOrdemServico.EM_DIAGNOSTICO);
        return os;
    }

    private ItemOrdemServico criarItemServico() {
        ItemOrdemServico itemOrdemServico = new ItemOrdemServico();
        OrdemServico ordemServico = new OrdemServico();
        ordemServico.setCodigo("OS-1");

        itemOrdemServico.setItemId(1L);
        itemOrdemServico.setItemNome("Filtro");
        itemOrdemServico.setOrdemServico(ordemServico);
        itemOrdemServico.setId(1L);
        itemOrdemServico.setQuantidade(10);

        return itemOrdemServico;
    }

    private ListaItensOrdemServicoResponse criarListaItens() {
        ItemOrdemServicoResponse item = new ItemOrdemServicoResponse(
                "OS-1",
                1L,
                "Filtro",
                10
        );

        return new ListaItensOrdemServicoResponse(List.of(item));
    }
}
