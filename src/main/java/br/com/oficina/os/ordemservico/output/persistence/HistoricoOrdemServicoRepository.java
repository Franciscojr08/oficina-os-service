package br.com.oficina.os.ordemservico.output.persistence;

import br.com.oficina.os.ordemservico.domain.HistoricoOrdemServico;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HistoricoOrdemServicoRepository extends JpaRepository<HistoricoOrdemServico, Long> {

	/** Ordena também pelo id porque duas mudanças podem cair no mesmo instante. */
	List<HistoricoOrdemServico> findByOrdemServicoIdOrderByDataCadastroAscIdAsc(Long ordemServicoId);
}
