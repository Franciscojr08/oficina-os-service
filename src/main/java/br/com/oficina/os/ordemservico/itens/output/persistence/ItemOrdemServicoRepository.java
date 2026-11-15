package br.com.oficina.os.ordemservico.itens.output.persistence;

import br.com.oficina.os.ordemservico.itens.domain.ItemOrdemServico;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ItemOrdemServicoRepository extends JpaRepository<ItemOrdemServico, Long> {

    List<ItemOrdemServico> findByOrdemServicoId(Long ordemServicoId);
}
