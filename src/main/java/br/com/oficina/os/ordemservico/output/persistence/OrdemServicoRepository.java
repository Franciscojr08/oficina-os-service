package br.com.oficina.os.ordemservico.output.persistence;

import br.com.oficina.os.ordemservico.application.StatusOrdemServico;
import br.com.oficina.os.ordemservico.domain.OrdemServico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface OrdemServicoRepository extends JpaRepository<OrdemServico, Long> {

    List<OrdemServico> findByClienteId(Long clienteId);

	Optional<OrdemServico> findByCodigo(String codigo);

    List<OrdemServico> findByStatus(StatusOrdemServico status);

	boolean existsByCodigo(String codigo);

	boolean existsByClienteIdAndStatusIn(Long clienteId, List<StatusOrdemServico> statusOrdemServicos);

	boolean existsByVeiculoIdAndStatusIn(Long veiculoId, List<StatusOrdemServico> statusOrdemServicos);

	List<OrdemServico> findByStatusIn(List<StatusOrdemServico> status);

	@Query("""
		SELECT os
		FROM OrdemServico os
		WHERE os.status NOT IN ('FINALIZADA', 'ENTREGUE','CANCELADA')
		ORDER BY
			CASE
				WHEN os.status = 'EM_EXECUCAO' THEN 1
				WHEN os.status = 'AGUARDANDO_ITENS' THEN 2
				WHEN os.status = 'APROVADA' THEN 3
				WHEN os.status = 'AGUARDANDO_APROVACAO' THEN 4
				WHEN os.status = 'EM_DIAGNOSTICO' THEN 5
				WHEN os.status = 'RECEBIDA' THEN 6
				ELSE 99
			END,
			os.dataCadastro ASC
	""")
	List<OrdemServico> listagemOrdensServico();
}
