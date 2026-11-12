package br.com.oficina.os.ordemservico.servicos.application;

import static br.com.oficina.os.shared.exception.ExceptionMessage.SERVICE_ORDER_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.oficina.os.ordemservico.application.OrdemServicoStatusService;
import br.com.oficina.os.ordemservico.application.StatusOrdemServico;
import br.com.oficina.os.ordemservico.application.gateway.OrdemServicoGateway;
import br.com.oficina.os.ordemservico.domain.OrdemServico;
import br.com.oficina.os.ordemservico.servicos.application.dto.ListaServicosOrdemServicoResponse;
import br.com.oficina.os.ordemservico.servicos.application.dto.ServicoOrdemServicoRequest;
import br.com.oficina.os.ordemservico.servicos.application.gateway.ServicoOrdemServicoGateway;
import br.com.oficina.os.ordemservico.servicos.domain.ServicoOrdemServico;
import br.com.oficina.os.shared.exception.RecursoNaoEncontradoException;
import br.com.oficina.os.shared.exception.RegraNegocioException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class ServicoOrdemServicoServiceTest {

	@Mock
	private ServicoOrdemServicoGateway servicoOrdemServicoGateway;

	@Mock
	private OrdemServicoGateway ordemServicoGateway;

	@Mock
	private OrdemServicoStatusService ordemServicoStatusService;

	@InjectMocks
	private ServicoOrdemServicoService service;

	@Test
	void deveGuardarOIdEACopiaDoNomeSemConsultarCatalogo() {
		OrdemServico os = criarOrdemServico(StatusOrdemServico.EM_DIAGNOSTICO);
		when(ordemServicoGateway.findById(1L)).thenReturn(Optional.of(os));

		service.adicionarServico(1L, new ServicoOrdemServicoRequest(30L, "  Alinhamento  "));

		ArgumentCaptor<ServicoOrdemServico> captor = ArgumentCaptor.forClass(ServicoOrdemServico.class);
		verify(servicoOrdemServicoGateway).save(captor.capture());
		assertThat(captor.getValue().getServicoId()).isEqualTo(30L);
		assertThat(captor.getValue().getServicoNome()).isEqualTo("Alinhamento");
		assertThat(captor.getValue().getOrdemServico()).isSameAs(os);
	}

	@Test
	void deveAdicionarServicoDuranteCadastroSemValidarDiagnostico() {
		OrdemServico os = criarOrdemServico(StatusOrdemServico.RECEBIDA);

		service.adicionarServicoDuranteCadastro(os, new ServicoOrdemServicoRequest(30L, "Alinhamento"));

		verify(ordemServicoStatusService, never()).validarEmDiagnostico(any(), anyString());
		verify(servicoOrdemServicoGateway).save(any(ServicoOrdemServico.class));
	}

	@Test
	void naoDeveAdicionarServicoForaDoDiagnostico() {
		OrdemServico os = criarOrdemServico(StatusOrdemServico.CANCELADA);
		when(ordemServicoGateway.findById(1L)).thenReturn(Optional.of(os));
		doThrow(new RegraNegocioException("fora do diagnóstico"))
				.when(ordemServicoStatusService).validarEmDiagnostico(eq(os), anyString());

		ServicoOrdemServicoRequest request = new ServicoOrdemServicoRequest(30L, "Alinhamento");
		assertThatThrownBy(() -> service.adicionarServico(1L, request))
				.isInstanceOf(RegraNegocioException.class);

		verify(servicoOrdemServicoGateway, never()).save(any());
	}

	@Test
	void deveFalharQuandoOrdemNaoExiste() {
		when(ordemServicoGateway.findById(99L)).thenReturn(Optional.empty());

		ServicoOrdemServicoRequest request = new ServicoOrdemServicoRequest(30L, "Alinhamento");
		assertThatThrownBy(() -> service.adicionarServico(99L, request))
				.isInstanceOf(RecursoNaoEncontradoException.class)
				.hasMessage(SERVICE_ORDER_NOT_FOUND);
	}

	@Test
	void deveListarServicos() {
		OrdemServico os = criarOrdemServico(StatusOrdemServico.EM_DIAGNOSTICO);
		when(servicoOrdemServicoGateway.findByOrdemServicoId(1L)).thenReturn(List.of(
				criarServicoOrdemServico(os),
				criarServicoOrdemServico(os)
		));

		ListaServicosOrdemServicoResponse response = service.listarServicosPorOrdemServico(1L);

		assertThat(response.servicos()).hasSize(2);
		assertThat(response.servicos().get(0).codigoOS()).isEqualTo("OS-2026-000001");
		assertThat(response.servicos().get(0).servicoId()).isEqualTo(30L);
		assertThat(response.servicos().get(0).servicoNome()).isEqualTo("Alinhamento");
	}

	private OrdemServico criarOrdemServico(StatusOrdemServico status) {
		OrdemServico os = new OrdemServico();
		os.setId(1L);
		os.setCodigo("OS-2026-000001");
		os.setStatus(status);
		return os;
	}

	private ServicoOrdemServico criarServicoOrdemServico(OrdemServico os) {
		ServicoOrdemServico servicoOs = new ServicoOrdemServico();
		servicoOs.setOrdemServico(os);
		servicoOs.setServicoId(30L);
		servicoOs.setServicoNome("Alinhamento");
		servicoOs.setDataCadastro(LocalDateTime.now());
		return servicoOs;
	}
}
