package br.com.oficina.os.ordemservico.servicos.output.persistence;

import br.com.oficina.os.ordemservico.servicos.domain.ServicoOrdemServico;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServicoOrdemServicoRepository extends JpaRepository<ServicoOrdemServico, Long> {

	List<ServicoOrdemServico> findByOrdemServicoId(Long ordemServicoId);
}
