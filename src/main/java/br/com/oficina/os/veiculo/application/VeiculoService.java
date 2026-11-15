package br.com.oficina.os.veiculo.application;

import static br.com.oficina.os.shared.exception.ExceptionMessage.CANNOT_INACTIVATE_VEHICLE_WITH_ACTIVE_SERVICE_ORDERS;
import static br.com.oficina.os.shared.exception.ExceptionMessage.CUSTOMER_NOT_FOUND;
import static br.com.oficina.os.shared.exception.ExceptionMessage.DUPLICATED_VEHICLE;
import static br.com.oficina.os.shared.exception.ExceptionMessage.INVALID_VEHICLE_PLATE;
import static br.com.oficina.os.shared.exception.ExceptionMessage.NOT_ACTIVE_CUSTOMER;
import static br.com.oficina.os.shared.exception.ExceptionMessage.VEHICLE_NOT_FOUND;

import br.com.oficina.os.cliente.application.gateway.ClienteGateway;
import br.com.oficina.os.cliente.domain.Cliente;
import br.com.oficina.os.ordemservico.application.StatusOrdemServico;
import br.com.oficina.os.ordemservico.application.gateway.OrdemServicoGateway;
import br.com.oficina.os.shared.exception.RecursoNaoEncontradoException;
import br.com.oficina.os.shared.exception.RegraNegocioException;
import br.com.oficina.os.shared.validator.ValidadorPlaca;
import br.com.oficina.os.veiculo.application.dto.VeiculoRequest;
import br.com.oficina.os.veiculo.application.dto.VeiculoResponse;
import br.com.oficina.os.veiculo.application.gateway.VeiculoGateway;
import br.com.oficina.os.veiculo.domain.Veiculo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VeiculoService {

    private final VeiculoGateway veiculoGateway;
    private final ClienteGateway clienteGateway;
	private final OrdemServicoGateway ordemServicoGateway;
	private final VeiculoMapper veiculoMapper;

    @Transactional
    public VeiculoResponse criar(VeiculoRequest request) {
        validarPlacaDuplicada(request.placa());

        Cliente cliente = buscarClientePorId(request.clienteId());

		validarCliente(cliente);

		Veiculo veiculo = new Veiculo();
		preencherVeiculo(veiculo, request, cliente);

        Veiculo salvo = veiculoGateway.save(veiculo);
        return veiculoMapper.toResponse(salvo);
    }

	private static void validarCliente(Cliente cliente) {
		if (Boolean.FALSE.equals(cliente.getAtivo())) {
			throw new RegraNegocioException(NOT_ACTIVE_CUSTOMER);
		}
	}

    public List<VeiculoResponse> listar() {
        return veiculoGateway.findAll()
                .stream()
                .map(veiculoMapper::toResponse)
                .toList();
    }

    public VeiculoResponse buscarPorId(Long id) {
        Veiculo veiculo = buscarVeiculoPorId(id);
        return veiculoMapper.toResponse(veiculo);
    }

    public List<VeiculoResponse> listarPorCliente(Long clienteId) {
        return veiculoGateway.findByClienteId(clienteId)
                .stream()
                .map(veiculoMapper::toResponse)
                .toList();
    }

    @Transactional
    public VeiculoResponse atualizar(Long id, VeiculoRequest request) {
		validarPlaca(request.placa());

        Veiculo veiculo = buscarVeiculoPorId(id);

        Cliente cliente = buscarClientePorId(request.clienteId());
		validarCliente(cliente);

        if (!veiculo.getPlaca().equals(request.placa()) &&
			veiculoGateway.existsByPlaca(request.placa())
        ) {
            throw new RegraNegocioException(DUPLICATED_VEHICLE);
        }

        preencherVeiculo(veiculo, request, cliente);
        Veiculo atualizado = veiculoGateway.save(veiculo);

        return veiculoMapper.toResponse(atualizado);
    }

    @Transactional
    public void inativar(Long id) {
        Veiculo veiculo = buscarVeiculoPorId(id);

		boolean possuiOrdemAtiva = ordemServicoGateway
				.existsByVeiculoIdAndStatusIn(id, StatusOrdemServico.statusAtivos());

		if (possuiOrdemAtiva) {
			throw new RegraNegocioException(CANNOT_INACTIVATE_VEHICLE_WITH_ACTIVE_SERVICE_ORDERS);
		}

        veiculo.setAtivo(false);
        veiculoGateway.save(veiculo);
    }

    @Transactional(readOnly = true)
    public VeiculoResponse buscarPorPlaca(String placa) {
        Veiculo veiculo = veiculoGateway.findByPlaca(placa)
                .orElseThrow(() -> new RecursoNaoEncontradoException(VEHICLE_NOT_FOUND));

        return veiculoMapper.toResponse(veiculo);
    }

    private void validarPlacaDuplicada(String placa) {
		validarPlaca(placa);

        if (veiculoGateway.existsByPlaca(placa)) {
            throw new RegraNegocioException(DUPLICATED_VEHICLE);
        }
    }

	private void validarPlaca(String placa) {
		boolean placaValida = ValidadorPlaca.isValida(placa);

		if (!placaValida) {
			throw new RegraNegocioException(INVALID_VEHICLE_PLATE);
		}
	}

    private Cliente buscarClientePorId(Long clienteId) {
        return clienteGateway.findById(clienteId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(CUSTOMER_NOT_FOUND));
    }

    private Veiculo buscarVeiculoPorId(Long id) {
        return veiculoGateway.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException(VEHICLE_NOT_FOUND));
    }

    private void preencherVeiculo(Veiculo veiculo, VeiculoRequest request, Cliente cliente) {
        veiculo.setCliente(cliente);
        veiculo.setPlaca(request.placa());
        veiculo.setMarca(request.marca());
        veiculo.setModelo(request.modelo());
        veiculo.setAno(request.ano());
        veiculo.setCor(request.cor());
        veiculo.setObservacao(request.observacao());
    }


}
