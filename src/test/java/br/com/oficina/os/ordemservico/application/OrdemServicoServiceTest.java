package br.com.oficina.os.ordemservico.application;

import static br.com.oficina.os.shared.exception.ExceptionMessage.CUSTOMER_NOT_FOUND;
import static br.com.oficina.os.shared.exception.ExceptionMessage.NOT_ACTIVE_CUSTOMER;
import static br.com.oficina.os.shared.exception.ExceptionMessage.NOT_ACTIVE_VEHICLE;
import static br.com.oficina.os.shared.exception.ExceptionMessage.SERVICE_ORDER_MUST_HAVE_ITEM_AND_SERVICE_TO_REQUEST_APPROVAL;
import static br.com.oficina.os.shared.exception.ExceptionMessage.SERVICE_ORDER_NOT_FOUND;
import static br.com.oficina.os.shared.exception.ExceptionMessage.VEHICLE_DOES_NOT_BELONG_TO_CUSTOMER;
import static br.com.oficina.os.shared.exception.ExceptionMessage.VEHICLE_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.oficina.os.cliente.application.gateway.ClienteGateway;
import br.com.oficina.os.cliente.domain.Cliente;
import br.com.oficina.os.mensageria.evento.EventoSaida;
import br.com.oficina.os.mensageria.outbox.OutboxService;
import br.com.oficina.os.ordemservico.application.dto.HistoricoOrdemServicoResponse;
import br.com.oficina.os.ordemservico.application.dto.OrdemServicoRequest;
import br.com.oficina.os.ordemservico.application.dto.OrdemServicoResponse;
import br.com.oficina.os.ordemservico.application.dto.StatusOrdemServicoResponse;
import br.com.oficina.os.ordemservico.application.evento.OrdemServicoStatusAlteradoDados;
import br.com.oficina.os.ordemservico.application.gateway.HistoricoOrdemServicoGateway;
import br.com.oficina.os.ordemservico.application.gateway.OrdemServicoGateway;
import br.com.oficina.os.ordemservico.domain.HistoricoOrdemServico;
import br.com.oficina.os.ordemservico.domain.OrdemServico;
import br.com.oficina.os.ordemservico.itens.application.ItemOrdemServicoService;
import br.com.oficina.os.ordemservico.itens.application.dto.ItemOrdemServicoRequest;
import br.com.oficina.os.ordemservico.itens.application.gateway.ItemOrdemServicoGateway;
import br.com.oficina.os.ordemservico.itens.domain.ItemOrdemServico;
import br.com.oficina.os.ordemservico.servicos.application.ServicoOrdemServicoService;
import br.com.oficina.os.ordemservico.servicos.application.dto.ServicoOrdemServicoRequest;
import br.com.oficina.os.ordemservico.servicos.application.gateway.ServicoOrdemServicoGateway;
import br.com.oficina.os.ordemservico.servicos.domain.ServicoOrdemServico;
import br.com.oficina.os.saga.application.OrquestradorSaga;
import br.com.oficina.os.saga.domain.SagaOrdemServicoRepository;
import br.com.oficina.os.shared.exception.RecursoNaoEncontradoException;
import br.com.oficina.os.shared.exception.RegraNegocioException;
import br.com.oficina.os.shared.observabilidade.OrdemServicoObservabilidade;
import br.com.oficina.os.shared.observabilidade.TelemetriaEmMemoria;
import br.com.oficina.os.veiculo.application.gateway.VeiculoGateway;
import br.com.oficina.os.veiculo.domain.Veiculo;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class OrdemServicoServiceTest {

	@Mock
	private OrdemServicoGateway repository;

	@Mock
	private ClienteGateway clienteRepository;

	@Mock
	private VeiculoGateway veiculoRepository;

	@Mock
	private ItemOrdemServicoGateway itemOrdemServicoGateway;

	@Mock
	private ServicoOrdemServicoGateway servicoOrdemServicoGateway;

	@Mock
	private HistoricoOrdemServicoGateway historicoOrdemServicoGateway;

	@Mock
	private EntityManager entityManager;

	@Mock
	private OutboxService outboxService;

	@Mock
	private OrquestradorSaga orquestradorSaga;

	@Mock
	private SagaOrdemServicoRepository sagaRepository;

	private OrdemServicoService ordemServicoService;

	private OrdemServicoRequest request;
	private Cliente clienteAtivo;
	private Veiculo veiculoAtivo;

	@BeforeEach
	void setUp() {
		HistoricoOrdemServicoService historicoService = new HistoricoOrdemServicoService(historicoOrdemServicoGateway);
		// A API do New Relic vira no-op sem o agent anexado, então não precisa de mock.
		OrdemServicoObservabilidade observabilidade = new OrdemServicoObservabilidade(new TelemetriaEmMemoria());
		OrdemServicoStatusService statusService =
				new OrdemServicoStatusService(repository, historicoService, observabilidade, outboxService, sagaRepository);

		ItemOrdemServicoService itemOrdemServicoService =
				new ItemOrdemServicoService(repository, itemOrdemServicoGateway, statusService);
		ServicoOrdemServicoService servicoOrdemServicoService =
				new ServicoOrdemServicoService(repository, servicoOrdemServicoGateway, statusService);

		ordemServicoService = new OrdemServicoService(
				repository,
				clienteRepository,
				veiculoRepository,
				itemOrdemServicoGateway,
				servicoOrdemServicoGateway,
				itemOrdemServicoService,
				servicoOrdemServicoService,
				historicoService,
				statusService,
				new OrdemServicoMapper(),
				orquestradorSaga
		);
		ReflectionTestUtils.setField(ordemServicoService, "entityManager", entityManager);

		request = new OrdemServicoRequest(
				"Barulho no motor",
				"Cliente relata ruído ao ligar",
				"Diagnóstico inicial",
				1L,
				10L,
				List.of(),
				List.of()
		);

		clienteAtivo = criarCliente(1L, true);
		veiculoAtivo = criarVeiculo(10L, true, clienteAtivo);
	}

	@Test
	void deveCriarOrdemServicoEmRecebidaComHistoricoEEventoDeStatus() {
		mockClienteEVeiculo();
		mockSaveAndFlushAtribuindoIdECodigo();

		OrdemServicoResponse response = ordemServicoService.criar(request);

		assertThat(response.id()).isEqualTo(100L);
		assertThat(response.codigo()).isEqualTo("OS-2026-000100");
		assertThat(response.status()).isEqualTo(StatusOrdemServico.RECEBIDA);
		assertThat(response.descricaoProblema()).isEqualTo("Barulho no motor");

		verify(entityManager).refresh(any(OrdemServico.class));
		ArgumentCaptor<HistoricoOrdemServico> historico = ArgumentCaptor.forClass(HistoricoOrdemServico.class);
		verify(historicoOrdemServicoGateway).save(historico.capture());
		assertThat(historico.getValue().getStatus()).isEqualTo(StatusOrdemServico.RECEBIDA);
		assertThat(historico.getValue().getOrigem()).isEqualTo(OrdemServicoStatusService.ORIGEM_API);

		ArgumentCaptor<Object> evento = ArgumentCaptor.forClass(Object.class);
		verify(outboxService).registrar(eq(EventoSaida.ORDEM_SERVICO_STATUS_ALTERADO), eq(100L), any(), isNull(), evento.capture());
		OrdemServicoStatusAlteradoDados abertura = (OrdemServicoStatusAlteradoDados) evento.getValue();
		assertThat(abertura.statusAnterior()).isNull();
		assertThat(abertura.statusAtual()).isEqualTo(StatusOrdemServico.RECEBIDA);
		assertThat(abertura.codigo()).isEqualTo("OS-2026-000100");
	}

	@Test
	void deveCriarOrdemServicoComServicosEItensDoCadastro() {
		mockClienteEVeiculo();
		mockSaveAndFlushAtribuindoIdECodigo();

		OrdemServicoRequest requestCompleto = new OrdemServicoRequest(
				"Barulho", null, "Diagnóstico", 1L, 10L,
				List.of(new ServicoOrdemServicoRequest(30L, "Troca de óleo")),
				List.of(new ItemOrdemServicoRequest(20L, "Filtro de óleo", 2))
		);

		OrdemServicoResponse response = ordemServicoService.criar(requestCompleto);

		assertThat(response.id()).isEqualTo(100L);
		verify(servicoOrdemServicoGateway).save(any(ServicoOrdemServico.class));
		verify(itemOrdemServicoGateway).save(any(ItemOrdemServico.class));
	}

	@Test
	void deveCriarOrdemServicoQuandoListasDoCadastroForemNulas() {
		mockClienteEVeiculo();
		mockSaveAndFlushAtribuindoIdECodigo();
		OrdemServicoRequest requestSemListas =
				new OrdemServicoRequest("Barulho", null, "Diagnóstico", 1L, 10L, null, null);

		OrdemServicoResponse response = ordemServicoService.criar(requestSemListas);

		assertThat(response.status()).isEqualTo(StatusOrdemServico.RECEBIDA);
		verify(servicoOrdemServicoGateway, never()).save(any());
		verify(itemOrdemServicoGateway, never()).save(any());
	}

	@Test
	void naoDeveCriarOrdemServicoComClienteInativo() {
		when(clienteRepository.findById(1L)).thenReturn(Optional.of(criarCliente(1L, false)));

		assertThatThrownBy(() -> ordemServicoService.criar(request))
				.isInstanceOf(RegraNegocioException.class)
				.hasMessage(NOT_ACTIVE_CUSTOMER);

		verify(veiculoRepository, never()).findById(anyLong());
		verify(repository, never()).saveAndFlush(any());
	}

	@Test
	void naoDeveCriarOrdemServicoComClienteInexistente() {
		when(clienteRepository.findById(1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> ordemServicoService.criar(request))
				.isInstanceOf(RecursoNaoEncontradoException.class)
				.hasMessage(CUSTOMER_NOT_FOUND);
	}

	@Test
	void naoDeveCriarOrdemServicoComVeiculoInativo() {
		when(clienteRepository.findById(1L)).thenReturn(Optional.of(clienteAtivo));
		when(veiculoRepository.findById(10L)).thenReturn(Optional.of(criarVeiculo(10L, false, clienteAtivo)));

		assertThatThrownBy(() -> ordemServicoService.criar(request))
				.isInstanceOf(RegraNegocioException.class)
				.hasMessage(NOT_ACTIVE_VEHICLE);

		verify(repository, never()).saveAndFlush(any());
	}

	@Test
	void naoDeveCriarOrdemServicoComVeiculoInexistente() {
		when(clienteRepository.findById(1L)).thenReturn(Optional.of(clienteAtivo));
		when(veiculoRepository.findById(10L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> ordemServicoService.criar(request))
				.isInstanceOf(RecursoNaoEncontradoException.class)
				.hasMessage(VEHICLE_NOT_FOUND);
	}

	@Test
	void naoDeveCriarOrdemServicoComVeiculoDeOutroCliente() {
		Cliente outroCliente = criarCliente(2L, true);
		when(clienteRepository.findById(1L)).thenReturn(Optional.of(clienteAtivo));
		when(veiculoRepository.findById(10L)).thenReturn(Optional.of(criarVeiculo(10L, true, outroCliente)));

		assertThatThrownBy(() -> ordemServicoService.criar(request))
				.isInstanceOf(RegraNegocioException.class)
				.hasMessage(VEHICLE_DOES_NOT_BELONG_TO_CUSTOMER);

		verify(repository, never()).saveAndFlush(any());
	}

	@Test
	void deveAtualizarOrdemServico() {
		OrdemServico os = criarOrdemServico(StatusOrdemServico.EM_DIAGNOSTICO);
		when(repository.findById(100L)).thenReturn(Optional.of(os));
		mockClienteEVeiculo();

		OrdemServicoResponse response = ordemServicoService.atualizar(100L, request);

		assertThat(response.descricaoProblema()).isEqualTo("Barulho no motor");
		assertThat(response.observacoesGerais()).isEqualTo("Cliente relata ruído ao ligar");
		assertThat(response.descricaoServicosExecutados()).isEqualTo("Diagnóstico inicial");
		verify(repository).saveAndFlush(os);
	}

	@Test
	void deveIniciarDiagnostico() {
		OrdemServico os = criarOrdemServico(StatusOrdemServico.RECEBIDA);
		when(repository.findById(100L)).thenReturn(Optional.of(os));

		OrdemServicoResponse response = ordemServicoService.iniciarDiagnostico(100L);

		assertThat(response.status()).isEqualTo(StatusOrdemServico.EM_DIAGNOSTICO);
		verify(historicoOrdemServicoGateway).save(any(HistoricoOrdemServico.class));
	}

	@Test
	void solicitarAprovacaoDeveManterADiagnosticoEIniciarASaga() {
		OrdemServico os = criarOrdemServico(StatusOrdemServico.EM_DIAGNOSTICO);
		os.setCliente(clienteAtivo);
		os.setVeiculo(veiculoAtivo);
		ItemOrdemServico item = new ItemOrdemServico();
		item.setItemId(20L);
		item.setItemNome("Filtro de óleo");
		item.setQuantidade(2);
		ServicoOrdemServico servico = new ServicoOrdemServico();
		servico.setServicoId(30L);
		servico.setServicoNome("Troca de óleo");
		when(itemOrdemServicoGateway.findByOrdemServicoId(100L)).thenReturn(List.of(item));
		when(servicoOrdemServicoGateway.findByOrdemServicoId(100L)).thenReturn(List.of(servico));
		when(repository.findById(100L)).thenReturn(Optional.of(os));

		OrdemServicoResponse response = ordemServicoService.solicitarAprovacao(100L);

		assertThat(response.status()).isEqualTo(StatusOrdemServico.EM_DIAGNOSTICO);
		assertThat(response.dataSolicitacaoOrcamento()).isNotNull();

		verify(orquestradorSaga).iniciar(os, List.of(item), List.of(servico));
	}

	@Test
	void naoDevePedirOrcamentoQuandoATransicaoEhRecusada() {
		when(repository.findById(100L)).thenReturn(Optional.of(criarOrdemServico(StatusOrdemServico.RECEBIDA)));
		when(itemOrdemServicoGateway.findByOrdemServicoId(100L)).thenReturn(List.of(new ItemOrdemServico()));
		when(servicoOrdemServicoGateway.findByOrdemServicoId(100L)).thenReturn(List.of(new ServicoOrdemServico()));

		assertThatThrownBy(() -> ordemServicoService.solicitarAprovacao(100L))
				.isInstanceOf(RegraNegocioException.class);

		verify(orquestradorSaga, never()).iniciar(any(), any(), any());
	}

	@Test
	void naoDeveSolicitarAprovacaoSemItens() {
		when(repository.findById(100L)).thenReturn(Optional.of(criarOrdemServico(StatusOrdemServico.EM_DIAGNOSTICO)));
		when(itemOrdemServicoGateway.findByOrdemServicoId(100L)).thenReturn(List.of());
		when(servicoOrdemServicoGateway.findByOrdemServicoId(100L)).thenReturn(List.of(new ServicoOrdemServico()));

		assertThatThrownBy(() -> ordemServicoService.solicitarAprovacao(100L))
				.isInstanceOf(RegraNegocioException.class)
				.hasMessage(SERVICE_ORDER_MUST_HAVE_ITEM_AND_SERVICE_TO_REQUEST_APPROVAL);

		verify(repository, never()).saveAndFlush(any());
	}

	@Test
	void naoDeveSolicitarAprovacaoSemServicos() {
		when(repository.findById(100L)).thenReturn(Optional.of(criarOrdemServico(StatusOrdemServico.EM_DIAGNOSTICO)));
		when(itemOrdemServicoGateway.findByOrdemServicoId(100L)).thenReturn(List.of(new ItemOrdemServico()));
		when(servicoOrdemServicoGateway.findByOrdemServicoId(100L)).thenReturn(List.of());

		assertThatThrownBy(() -> ordemServicoService.solicitarAprovacao(100L))
				.isInstanceOf(RegraNegocioException.class)
				.hasMessage(SERVICE_ORDER_MUST_HAVE_ITEM_AND_SERVICE_TO_REQUEST_APPROVAL);
	}

	@Test
	void deveEntregarOrdemFinalizada() {
		OrdemServico os = criarOrdemServico(StatusOrdemServico.FINALIZADA);
		os.setDataFimExecucao(LocalDateTime.now().minusHours(1));
		when(repository.findById(100L)).thenReturn(Optional.of(os));

		OrdemServicoResponse response = ordemServicoService.entregarOrdemServico(100L);

		assertThat(response.status()).isEqualTo(StatusOrdemServico.ENTREGUE);
		assertThat(response.dataEntregue()).isNotNull();
	}

	@Test
	void naoDeveEntregarOrdemQueNaoEstaFinalizada() {
		when(repository.findById(100L)).thenReturn(Optional.of(criarOrdemServico(StatusOrdemServico.EM_EXECUCAO)));

		assertThatThrownBy(() -> ordemServicoService.entregarOrdemServico(100L))
				.isInstanceOf(RegraNegocioException.class)
				.hasMessageContaining("Em execução");
	}

	@Test
	void deveConsultarStatus() {
		when(repository.findById(100L)).thenReturn(Optional.of(criarOrdemServico(StatusOrdemServico.AGUARDANDO_APROVACAO)));

		StatusOrdemServicoResponse response = ordemServicoService.consultarStatus(100L);

		assertThat(response.codigo()).isEqualTo("OS-2026-000100");
		assertThat(response.status()).isEqualTo(StatusOrdemServico.AGUARDANDO_APROVACAO);
	}

	@Test
	void deveFalharQuandoOrdemNaoExiste() {
		when(repository.findById(999L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> ordemServicoService.consultarStatus(999L))
				.isInstanceOf(RecursoNaoEncontradoException.class)
				.hasMessage(SERVICE_ORDER_NOT_FOUND);
	}

	@Test
	void deveListarOrdensAtivas() {
		when(repository.listagemOrdensServico()).thenReturn(List.of(criarOrdemServico(StatusOrdemServico.RECEBIDA)));

		List<OrdemServicoResponse> out = ordemServicoService.listar();

		assertThat(out).singleElement().satisfies(os -> {
			assertThat(os.id()).isEqualTo(100L);
			assertThat(os.status()).isEqualTo(StatusOrdemServico.RECEBIDA);
		});
	}

	@Test
	void deveListarPorClienteCodigoEStatus() {
		OrdemServico os = criarOrdemServico(StatusOrdemServico.RECEBIDA);
		when(repository.findByClienteId(1L)).thenReturn(List.of(os));
		when(repository.findByCodigo("OS-2026-000100")).thenReturn(Optional.of(os));
		when(repository.findByStatus(StatusOrdemServico.RECEBIDA)).thenReturn(List.of(os));

		assertThat(ordemServicoService.listarPorCliente(1L)).extracting(OrdemServicoResponse::id).containsExactly(100L);
		assertThat(ordemServicoService.listarPorCodigo("OS-2026-000100")).extracting(OrdemServicoResponse::id)
				.containsExactly(100L);
		assertThat(ordemServicoService.listarPorStatus(StatusOrdemServico.RECEBIDA))
				.extracting(OrdemServicoResponse::id).containsExactly(100L);
		verify(repository, times(1)).findByCodigo("OS-2026-000100");
	}

	@Test
	void deveBuscarOrdemServicoPorId() {
		when(repository.findById(100L)).thenReturn(Optional.of(criarOrdemServico(StatusOrdemServico.RECEBIDA)));

		OrdemServicoResponse response = ordemServicoService.buscarPorId(100L);

		assertThat(response.id()).isEqualTo(100L);
		assertThat(response.codigo()).isEqualTo("OS-2026-000100");
	}

	@Test
	void buscarPorIdDeveFalharQuandoOrdemNaoExiste() {
		when(repository.findById(999L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> ordemServicoService.buscarPorId(999L))
				.isInstanceOf(RecursoNaoEncontradoException.class)
				.hasMessage(SERVICE_ORDER_NOT_FOUND);
	}

	@Test
	void deveAtualizarOrdemServicoRecebida() {
		OrdemServico os = criarOrdemServico(StatusOrdemServico.RECEBIDA);
		when(repository.findById(100L)).thenReturn(Optional.of(os));
		mockClienteEVeiculo();

		ordemServicoService.atualizar(100L, request);

		verify(repository).saveAndFlush(os);
	}

	@ParameterizedTest
	@EnumSource(value = StatusOrdemServico.class, names = {"RECEBIDA", "EM_DIAGNOSTICO"}, mode = EnumSource.Mode.EXCLUDE)
	void naoDeveAtualizarOrdemServicoDepoisDoPedidoDeOrcamento(StatusOrdemServico status) {
		OrdemServico os = criarOrdemServico(status);
		when(repository.findById(100L)).thenReturn(Optional.of(os));

		assertThatThrownBy(() -> ordemServicoService.atualizar(100L, request))
				.isInstanceOf(RegraNegocioException.class)
				.hasMessageContaining("alterar a ordem de serviço")
				.hasMessageContaining(status.getDescricao());

		verify(repository, never()).saveAndFlush(any());
		verify(clienteRepository, never()).findById(anyLong());
	}

	@Test
	void solicitarAprovacaoDeveResponderNaoEncontradaAntesDeChecarItens() {
		when(repository.findById(999L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> ordemServicoService.solicitarAprovacao(999L))
				.isInstanceOf(RecursoNaoEncontradoException.class)
				.hasMessage(SERVICE_ORDER_NOT_FOUND);

		verify(itemOrdemServicoGateway, never()).findByOrdemServicoId(anyLong());
	}

	@Test
	void cancelarDeveDelegarParaASaga() {
		OrdemServico os = criarOrdemServico(StatusOrdemServico.AGUARDANDO_APROVACAO);
		when(repository.findById(100L)).thenReturn(Optional.of(os));

		ordemServicoService.cancelar(100L);

		verify(orquestradorSaga).cancelar(os);
	}

	@Test
	void aprovarDeveDelegarParaASaga() {
		OrdemServico os = criarOrdemServico(StatusOrdemServico.AGUARDANDO_APROVACAO);
		when(repository.findById(100L)).thenReturn(Optional.of(os));

		ordemServicoService.aprovar(100L);

		verify(orquestradorSaga).aprovar(os);
	}

	@Test
	void reprovarDeveDelegarParaASaga() {
		OrdemServico os = criarOrdemServico(StatusOrdemServico.AGUARDANDO_APROVACAO);
		when(repository.findById(100L)).thenReturn(Optional.of(os));

		ordemServicoService.reprovar(100L);

		verify(orquestradorSaga).reprovar(os);
	}

	@Test
	void aprovarEReprovarDevemFalharQuandoOrdemNaoExiste() {
		when(repository.findById(999L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> ordemServicoService.aprovar(999L)).isInstanceOf(RecursoNaoEncontradoException.class);
		assertThatThrownBy(() -> ordemServicoService.reprovar(999L)).isInstanceOf(RecursoNaoEncontradoException.class);
		verify(orquestradorSaga, never()).aprovar(any());
		verify(orquestradorSaga, never()).reprovar(any());
	}

	@Test
	void cancelarDeveFalharQuandoOrdemNaoExiste() {
		when(repository.findById(999L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> ordemServicoService.cancelar(999L))
				.isInstanceOf(RecursoNaoEncontradoException.class);
	}

	@Test
	void deveConsultarHistoricoEmOrdemCronologica() {
		OrdemServico os = criarOrdemServico(StatusOrdemServico.EM_DIAGNOSTICO);
		when(repository.findById(100L)).thenReturn(Optional.of(os));
		when(historicoOrdemServicoGateway.listarPorOrdemServico(100L)).thenReturn(List.of(
				criarHistorico(os, StatusOrdemServico.RECEBIDA, "API"),
				criarHistorico(os, StatusOrdemServico.EM_DIAGNOSTICO, "API")
		));

		List<HistoricoOrdemServicoResponse> historico = ordemServicoService.consultarHistorico(100L);

		assertThat(historico).extracting(HistoricoOrdemServicoResponse::status)
				.containsExactly(StatusOrdemServico.RECEBIDA, StatusOrdemServico.EM_DIAGNOSTICO);
		assertThat(historico.get(1).descricao()).isEqualTo("Em diagnóstico");
		assertThat(historico.get(1).origem()).isEqualTo("API");
		assertThat(historico.get(1).data()).isNotNull();
	}

	@Test
	void consultarHistoricoDeveFalharQuandoOrdemNaoExiste() {
		when(repository.findById(999L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> ordemServicoService.consultarHistorico(999L))
				.isInstanceOf(RecursoNaoEncontradoException.class);

		verify(historicoOrdemServicoGateway, never()).listarPorOrdemServico(anyLong());
	}

	private HistoricoOrdemServico criarHistorico(OrdemServico os, StatusOrdemServico status, String origem) {
		HistoricoOrdemServico historico = new HistoricoOrdemServico();
		historico.setOrdemServico(os);
		historico.setStatus(status);
		historico.setOrigem(origem);
		historico.setDataCadastro(LocalDateTime.now());
		return historico;
	}

	private void mockClienteEVeiculo() {
		when(clienteRepository.findById(1L)).thenReturn(Optional.of(clienteAtivo));
		when(veiculoRepository.findById(10L)).thenReturn(Optional.of(veiculoAtivo));
	}

	private void mockSaveAndFlushAtribuindoIdECodigo() {
		when(repository.saveAndFlush(any(OrdemServico.class))).thenAnswer(invocation -> {
			OrdemServico os = invocation.getArgument(0);
			if (os.getId() == null) {
				os.setId(100L);
				os.setCodigo("OS-2026-000100");
				os.setDataCadastro(LocalDateTime.now());
			}
			return os;
		});
	}

	private Cliente criarCliente(Long id, boolean ativo) {
		Cliente cliente = new Cliente();
		cliente.setId(id);
		cliente.setAtivo(ativo);
		return cliente;
	}

	private Veiculo criarVeiculo(Long id, boolean ativo, Cliente cliente) {
		Veiculo veiculo = new Veiculo();
		veiculo.setId(id);
		veiculo.setAtivo(ativo);
		veiculo.setCliente(cliente);
		return veiculo;
	}

	private OrdemServico criarOrdemServico(StatusOrdemServico status) {
		OrdemServico os = new OrdemServico();
		os.setId(100L);
		os.setCodigo("OS-2026-000100");
		os.setStatus(status);
		os.setDataCadastro(LocalDateTime.now().minusDays(1));
		return os;
	}
}
