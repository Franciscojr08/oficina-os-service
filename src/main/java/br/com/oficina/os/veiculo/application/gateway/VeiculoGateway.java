package br.com.oficina.os.veiculo.application.gateway;

import br.com.oficina.os.veiculo.domain.Veiculo;

import java.util.List;
import java.util.Optional;

public interface VeiculoGateway {

	Veiculo save(Veiculo veiculo);

	List<Veiculo> findAll();

	Optional<Veiculo> findById(Long id);

	boolean existsByPlaca(String placa);

	Optional<Veiculo> findByPlaca(String placa);

	List<Veiculo> findByClienteId(Long clienteId);
}
