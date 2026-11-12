package br.com.oficina.os.mensageria.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface OutboxEventoRepository extends JpaRepository<OutboxEvento, Long> {

	/**
	 * Pendentes em ordem de criação, travados para esta transação. O {@code SKIP LOCKED} deixa
	 * várias réplicas do serviço publicarem ao mesmo tempo sem enviar o mesmo evento duas vezes.
	 */
	@Query(value = """
			SELECT * FROM outbox_evento
			WHERE publicado_em IS NULL
			ORDER BY id
			LIMIT :lote
			FOR UPDATE SKIP LOCKED
			""", nativeQuery = true)
	List<OutboxEvento> travarPendentes(@Param("lote") int lote);

	long countByPublicadoEmIsNull();

	List<OutboxEvento> findByOrdemServicoIdOrderByIdAsc(Long ordemServicoId);

	OutboxEvento findByMessageId(UUID messageId);

	@Modifying
	@Query("UPDATE OutboxEvento e SET e.publicadoEm = NULL WHERE e.messageId = :messageId")
	int marcarParaReenvio(@Param("messageId") UUID messageId);

	@Modifying
	@Query("DELETE FROM OutboxEvento e WHERE e.publicadoEm < :limite")
	int removerPublicadosAntesDe(@Param("limite") LocalDateTime limite);
}
