package br.com.oficina.os.ordemservico.application;

import static br.com.oficina.os.shared.exception.ExceptionMessage.CUSTOMER_NOT_FOUND;
import static br.com.oficina.os.shared.exception.ExceptionMessage.FORBIDDEN_OTHER_CUSTOMER_ORDERS;
import static br.com.oficina.os.shared.exception.ExceptionMessage.NOT_ACTIVE_CUSTOMER;
import static br.com.oficina.os.shared.exception.ExceptionMessage.NOT_ACTIVE_VEHICLE;
import static br.com.oficina.os.shared.exception.ExceptionMessage.SERVICE_ORDER_MUST_HAVE_ITEM_AND_SERVICE_TO_REQUEST_APPROVAL;
import static br.com.oficina.os.shared.exception.ExceptionMessage.SERVICE_ORDER_NOT_FOUND;
import static br.com.oficina.os.shared.exception.ExceptionMessage.VEHICLE_DOES_NOT_BELONG_TO_CUSTOMER;
import static br.com.oficina.os.shared.exception.ExceptionMessage.VEHICLE_NOT_FOUND;

import br.com.oficina.os.cliente.application.gateway.ClienteGateway;
import br.com.oficina.os.cliente.domain.Cliente;
import br.com.oficina.os.ordemservico.application.dto.HistoricoOrdemServicoResponse;
import br.com.oficina.os.ordemservico.application.dto.OrdemServicoRequest;
import br.com.oficina.os.ordemservico.application.dto.OrdemServicoResponse;
import br.com.oficina.os.ordemservico.application.dto.StatusOrdemServicoResponse;
import br.com.oficina.os.ordemservico.application.gateway.OrdemServicoGateway;
import br.com.oficina.os.ordemservico.domain.OrdemServico;
import br.com.oficina.os.ordemservico.itens.application.ItemOrdemServicoService;
import br.com.oficina.os.ordemservico.itens.application.gateway.ItemOrdemServicoGateway;
import br.com.oficina.os.ordemservico.itens.domain.ItemOrdemServico;
import br.com.oficina.os.ordemservico.servicos.application.ServicoOrdemServicoService;
import br.com.oficina.os.ordemservico.servicos.application.gateway.ServicoOrdemServicoGateway;
import br.com.oficina.os.ordemservico.servicos.domain.ServicoOrdemServico;
import br.com.oficina.os.saga.application.OrquestradorSaga;
import br.com.oficina.os.security.ClienteAutenticadoContexto;
import br.com.oficina.os.shared.exception.AcessoNegadoException;
import br.com.oficina.os.shared.exception.RecursoNaoEncontradoException;
import br.com.oficina.os.shared.exception.RegraNegocioException;
import br.com.oficina.os.veiculo.application.gateway.VeiculoGateway;
import br.com.oficina.os.veiculo.domain.Veiculo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@Service
@RequiredArgsConstructor
public class OrdemServicoService {

    private final OrdemServicoGateway ordemServicoRepository;
    private final ClienteGateway clienteRepository;
    private final VeiculoGateway veiculoRepository;
    private final ItemOrdemServicoGateway itemOrdemServicoRepository;
    private final ServicoOrdemServicoGateway servicoOrdemServicoRepository;
	private final ItemOrdemServicoService itemOrdemServicoService;
	private final ServicoOrdemServicoService servicoOrdemServicoService;
	private final HistoricoOrdemServicoService historicoOrdemServicoService;
	private final OrdemServicoStatusService ordemServicoStatusService;
	private final OrdemServicoMapper ordemServicoMapper;
	private final OrquestradorSaga orquestradorSaga;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<OrdemServicoResponse> listar() {
        return ordemServicoRepository.listagemOrdensServico()
                .stream()
                .map(ordemServicoMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrdemServicoResponse buscarPorId(Long id) {
        return ordemServicoMapper.toResponse(buscarOrdemServicoDoSolicitante(id));
    }

    @Transactional(readOnly = true)
    public List<OrdemServicoResponse> listarPorCliente(Long clienteId) {
        ClienteAutenticadoContexto.clienteId()
                .filter(clienteAutenticado -> !clienteAutenticado.equals(clienteId))
                .ifPresent(outro -> {
                    throw new AcessoNegadoException(FORBIDDEN_OTHER_CUSTOMER_ORDERS);
                });

        return ordemServicoRepository.findByClienteId(clienteId)
                .stream()
                .map(ordemServicoMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrdemServicoResponse> listarPorCodigo(String codigo) {
        return ordemServicoRepository.findByCodigo(codigo)
                .stream()
                .map(ordemServicoMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrdemServicoResponse> listarPorStatus(StatusOrdemServico status) {
        return ordemServicoRepository.findByStatus(status)
                .stream()
                .map(ordemServicoMapper::toResponse)
                .toList();
    }

	@Transactional
	public OrdemServicoResponse criar(OrdemServicoRequest request) {
		Cliente cliente = buscarClientePorId(request.clienteId());
		Veiculo veiculo = buscarVeiculoPorId(request.veiculoId());

		validarVeiculoPertenceAoCliente(veiculo, cliente);

        OrdemServico ordemServico = new OrdemServico();
		ordemServicoStatusService.definirStatusInicial(ordemServico);

		preencherOrdemServico(
			ordemServico,
			request,
			veiculo,
			cliente
		);

		ordemServicoRepository.saveAndFlush(ordemServico);
		entityManager.refresh(ordemServico);

		ordemServicoStatusService.registrarAbertura(ordemServico);
		adicionarServicosDoCadastro(ordemServico, request);
		adicionarItensDoCadastro(ordemServico, request);

        return ordemServicoMapper.toResponse(ordemServico);
    }

	private void adicionarServicosDoCadastro(OrdemServico ordemServico, OrdemServicoRequest request) {
		if (request.servicos() == null) {
			return;
		}

		request.servicos().forEach(servico ->
				servicoOrdemServicoService.adicionarServicoDuranteCadastro(ordemServico, servico)
		);
	}

	private void adicionarItensDoCadastro(OrdemServico ordemServico, OrdemServicoRequest request) {
		if (request.itens() == null) {
			return;
		}

		request.itens().forEach(item ->
				itemOrdemServicoService.adicionarItemDuranteCadastro(ordemServico, item)
		);
	}

	@Transactional
    public OrdemServicoResponse atualizar(Long id, OrdemServicoRequest request) {
        OrdemServico ordemServico = buscarOrdemServicoPorId(id);
		ordemServicoStatusService.validarPermiteAlteracao(ordemServico);

		Cliente cliente = buscarClientePorId(request.clienteId());
		Veiculo veiculo = buscarVeiculoPorId(request.veiculoId());

		validarVeiculoPertenceAoCliente(veiculo, cliente);

		preencherOrdemServico(
			ordemServico,
			request,
			veiculo,
			cliente
		);
		ordemServicoRepository.saveAndFlush(ordemServico);

		return ordemServicoMapper.toResponse(ordemServico);
	}

	private void validarVeiculoPertenceAoCliente(Veiculo veiculo, Cliente cliente) {
		if (!veiculo.getCliente().getId().equals(cliente.getId())) {
			throw new RegraNegocioException(VEHICLE_DOES_NOT_BELONG_TO_CUSTOMER);
		}
	}

    private Cliente buscarClientePorId(Long clienteId) {
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(CUSTOMER_NOT_FOUND));

		if (Boolean.FALSE.equals(cliente.getAtivo())) {
			throw new RegraNegocioException(NOT_ACTIVE_CUSTOMER);
		}

		return cliente;
    }

    private Veiculo buscarVeiculoPorId(Long id) {
        Veiculo veiculo = veiculoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException(VEHICLE_NOT_FOUND));

		if (Boolean.FALSE.equals(veiculo.getAtivo())) {
			throw new RegraNegocioException(NOT_ACTIVE_VEHICLE);
		}

		return veiculo;
    }

    private OrdemServico buscarOrdemServicoPorId(Long id) {
        return ordemServicoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException(SERVICE_ORDER_NOT_FOUND));
    }

    /**
     * Para operadores, igual a {@link #buscarOrdemServicoPorId(Long)}. Para clientes, a OS de outro
     * cliente responde como inexistente, para não revelar que ela existe.
     */
    private OrdemServico buscarOrdemServicoDoSolicitante(Long id) {
        OrdemServico ordemServico = buscarOrdemServicoPorId(id);

        ClienteAutenticadoContexto.clienteId()
                .filter(clienteId -> !clienteId.equals(ordemServico.getCliente().getId()))
                .ifPresent(outro -> {
                    throw new RecursoNaoEncontradoException(SERVICE_ORDER_NOT_FOUND);
                });

        return ordemServico;
    }

    private void preencherOrdemServico(
		OrdemServico ordemServico,
		OrdemServicoRequest request,
		Veiculo veiculo,
		Cliente cliente
	) {
        ordemServico.setCliente(cliente);
        ordemServico.setVeiculo(veiculo);
        ordemServico.setDescricaoProblema(request.descricaoProblema());
        ordemServico.setObservacoesGerais(request.observacoesGerais());
        ordemServico.setDescricaoServicosExecutados(request.descricaoServicosExecutados());
    }

	@Transactional
	public OrdemServicoResponse iniciarDiagnostico(Long id) {
		OrdemServico ordemServico = buscarOrdemServicoPorId(id);

		ordemServicoStatusService.iniciarDiagnostico(ordemServico);

		return ordemServicoMapper.toResponse(ordemServico);
	}

	/**
	 * Fecha o diagnóstico e inicia a Saga: a OS segue em EM_DIAGNOSTICO e o comando
	 * {@code SolicitarOrcamento} vai para o Billing, na mesma transação.
	 */
	@Transactional
	public OrdemServicoResponse solicitarAprovacao(Long id) {
		OrdemServico ordemServico = buscarOrdemServicoPorId(id);

		List<ItemOrdemServico> itemOrdemServicoList = itemOrdemServicoRepository.findByOrdemServicoId(id);
		List<ServicoOrdemServico> servicoOrdemServicoList = servicoOrdemServicoRepository.findByOrdemServicoId(id);

		if (itemOrdemServicoList.isEmpty() || servicoOrdemServicoList.isEmpty()) {
			throw new RegraNegocioException(SERVICE_ORDER_MUST_HAVE_ITEM_AND_SERVICE_TO_REQUEST_APPROVAL);
		}

		ordemServicoStatusService.solicitarOrcamento(ordemServico);
		orquestradorSaga.iniciar(ordemServico, itemOrdemServicoList, servicoOrdemServicoList);

		return ordemServicoMapper.toResponse(ordemServico);
	}

	/** O cliente (ou a oficina por ele) aprovou o orçamento: a Saga pede a cobrança ao Billing. */
	@Transactional
	public OrdemServicoResponse aprovar(Long id) {
		OrdemServico ordemServico = buscarOrdemServicoDoSolicitante(id);

		orquestradorSaga.aprovar(ordemServico);

		return ordemServicoMapper.toResponse(ordemServico);
	}

	/** O cliente reprovou o orçamento: a OS é cancelada, sem nada a estornar. */
	@Transactional
	public OrdemServicoResponse reprovar(Long id) {
		OrdemServico ordemServico = buscarOrdemServicoDoSolicitante(id);

		orquestradorSaga.reprovar(ordemServico);

		return ordemServicoMapper.toResponse(ordemServico);
	}

	@Transactional
	public OrdemServicoResponse entregarOrdemServico(Long id) {
		OrdemServico ordemServico = buscarOrdemServicoPorId(id);

		ordemServicoStatusService.entregar(ordemServico);

		return ordemServicoMapper.toResponse(ordemServico);
	}

	/**
	 * Cancelamento pedido pelo cliente ou pela oficina. OS sem pagamento é cancelada na hora; OS paga
	 * continua no status atual até o Billing confirmar o estorno (a resposta mostra isso).
	 */
	@Transactional
	public OrdemServicoResponse cancelar(Long id) {
		OrdemServico ordemServico = buscarOrdemServicoDoSolicitante(id);

		orquestradorSaga.cancelar(ordemServico);

		return ordemServicoMapper.toResponse(ordemServico);
	}

	@Transactional(readOnly = true)
	public List<HistoricoOrdemServicoResponse> consultarHistorico(Long id) {
		buscarOrdemServicoDoSolicitante(id);

		return historicoOrdemServicoService.listar(id);
	}

	@Transactional(readOnly = true)
	public StatusOrdemServicoResponse consultarStatus(Long id) {
		OrdemServico ordemServico = buscarOrdemServicoDoSolicitante(id);

		return new StatusOrdemServicoResponse(ordemServico.getCodigo(),ordemServico.getStatus());
	}
}
