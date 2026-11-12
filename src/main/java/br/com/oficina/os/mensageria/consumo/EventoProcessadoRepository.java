package br.com.oficina.os.mensageria.consumo;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.UUID;

/** Inbox: chaves das mensagens já consumidas (ver {@link ChaveDeduplicacao}). */
@Repository
@RequiredArgsConstructor
public class EventoProcessadoRepository {

	private final JdbcTemplate jdbcTemplate;

	/**
	 * Registra a mensagem e devolve {@code false} se ela já tinha sido processada. O
	 * {@code ON CONFLICT DO NOTHING} resolve também duas entregas simultâneas: a segunda espera a
	 * primeira terminar e só insere se a primeira tiver sido desfeita.
	 */
	public boolean registrar(String chave, UUID messageId, UUID sagaId, String tipo, Long ordemServicoId) {
		int inseridas = jdbcTemplate.update("""
				INSERT INTO evento_processado (chave_deduplicacao, message_id, saga_id, tipo, ordem_servico_id)
				VALUES (?, ?, ?, ?, ?)
				ON CONFLICT (chave_deduplicacao) DO NOTHING
				""", chave, messageId, sagaId, tipo, ordemServicoId);
		return inseridas == 1;
	}

	public boolean foiProcessado(String chave) {
		Integer total = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM evento_processado WHERE chave_deduplicacao = ?", Integer.class, chave);
		return total != null && total > 0;
	}

	/** Atalho para mensagens que trouxeram {@code messageId}, cuja chave é o próprio id. */
	public boolean foiProcessado(UUID messageId) {
		return foiProcessado(messageId.toString());
	}

	public int removerProcessadosAntesDe(LocalDateTime limite) {
		return jdbcTemplate.update("DELETE FROM evento_processado WHERE processado_em < ?", limite);
	}
}
