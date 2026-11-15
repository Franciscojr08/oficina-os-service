package br.com.oficina.os.saga.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SagaOrdemServicoRepository extends JpaRepository<SagaOrdemServico, Long> {

	Optional<SagaOrdemServico> findByOrdemServicoId(Long ordemServicoId);

	/**
	 * Saga travada para esta transação. Serializa, por OS, o consumo de eventos, o cancelamento pela
	 * API e o verificador de prazos.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT s FROM SagaOrdemServico s WHERE s.ordemServicoId = :ordemServicoId")
	Optional<SagaOrdemServico> travarPorOrdemServico(@Param("ordemServicoId") Long ordemServicoId);

	@Query(value = """
			SELECT id FROM saga_ordem_servico
			WHERE situacao IN ('EM_ANDAMENTO', 'COMPENSANDO') AND prazo < :agora
			ORDER BY prazo
			LIMIT :lote
			""", nativeQuery = true)
	List<Long> idsComPrazoVencido(@Param("agora") LocalDateTime agora, @Param("lote") int lote);

	/** Trava a Saga se ela ainda estiver vencida; pula se outra réplica já a estiver tratando. */
	@Query(value = """
			SELECT * FROM saga_ordem_servico
			WHERE id = :id AND situacao IN ('EM_ANDAMENTO', 'COMPENSANDO') AND prazo < :agora
			FOR UPDATE SKIP LOCKED
			""", nativeQuery = true)
	Optional<SagaOrdemServico> travarSeVencida(@Param("id") Long id, @Param("agora") LocalDateTime agora);
}
