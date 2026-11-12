package br.com.oficina.os.cliente.output.persistence;

import br.com.oficina.os.cliente.domain.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

	Optional<Cliente> findByCpfCnpj(String cpfCnpj);

	boolean existsByCpfCnpj(String cpfCnpj);

}
