package br.com.oficina.os.mensageria.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/** Evento gravado na mesma transação da mudança na OS, à espera de publicação. */
@Getter
@Setter
@Entity
@Table(name = "outbox_evento")
public class OutboxEvento {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "message_id", nullable = false, unique = true)
	private UUID messageId;

	@Column(name = "tipo", nullable = false, length = 60)
	private String tipo;

	@Column(name = "exchange", nullable = false, length = 100)
	private String exchange;

	@Column(name = "routing_key", nullable = false, length = 100)
	private String routingKey;

	@Column(name = "ordem_servico_id")
	private Long ordemServicoId;

	/** Saga a que a mensagem pertence (nulo em evento informativo fora de uma Saga). */
	@Column(name = "saga_id")
	private UUID sagaId;

	@Column(name = "correlation_id", nullable = false, length = 128)
	private String correlationId;

	@Column(name = "payload", nullable = false, columnDefinition = "TEXT")
	private String payload;

	@Column(name = "criado_em", nullable = false)
	private LocalDateTime criadoEm;

	@Column(name = "publicado_em")
	private LocalDateTime publicadoEm;

	@Column(name = "tentativas", nullable = false)
	private int tentativas;

	@Column(name = "ultimo_erro", length = 500)
	private String ultimoErro;

	/** Cabeçalhos do trace da transação que gravou o evento, em JSON (nulo sem trace ativo). */
	@Column(name = "rastreamento", columnDefinition = "TEXT")
	private String rastreamento;

	@PrePersist
	void prePersist() {
		this.criadoEm = LocalDateTime.now();
	}
}
