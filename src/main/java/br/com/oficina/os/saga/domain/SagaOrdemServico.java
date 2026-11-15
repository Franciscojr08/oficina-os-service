package br.com.oficina.os.saga.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/** Estado da Saga de uma OS. Uma linha por OS; um novo pedido de orçamento reinicia a Saga. */
@Getter
@Setter
@Entity
@Table(name = "saga_ordem_servico")
public class SagaOrdemServico {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "ordem_servico_id", nullable = false, unique = true)
	private Long ordemServicoId;

	/** Id do ciclo atual, enviado em todas as mensagens da Saga. Um novo pedido de orçamento gera outro. */
	@Column(name = "saga_id", nullable = false, unique = true)
	private UUID sagaId;

	@Enumerated(EnumType.STRING)
	@Column(name = "etapa", nullable = false, length = 40)
	private EtapaSaga etapa;

	@Enumerated(EnumType.STRING)
	@Column(name = "situacao", nullable = false, length = 30)
	private SituacaoSaga situacao;

	@Column(name = "prazo")
	private LocalDateTime prazo;

	@Column(name = "tentativas", nullable = false)
	private int tentativas;

	@Column(name = "comando_message_id")
	private UUID comandoMessageId;

	@Column(name = "motivo")
	private String motivo;

	@Column(name = "criado_em", nullable = false)
	private LocalDateTime criadoEm;

	@Column(name = "atualizado_em")
	private LocalDateTime atualizadoEm;

	@Column(name = "encerrada_em")
	private LocalDateTime encerradaEm;

	/** Início do ciclo atual (um novo pedido de orçamento reinicia a Saga). */
	@Column(name = "iniciada_em")
	private LocalDateTime iniciadaEm;

	public static SagaOrdemServico para(Long ordemServicoId) {
		SagaOrdemServico saga = new SagaOrdemServico();
		saga.setOrdemServicoId(ordemServicoId);
		return saga;
	}

	/** Começa (ou recomeça) a Saga com o pedido de orçamento, num ciclo identificado por {@code novoSagaId}. */
	public void iniciar(UUID novoSagaId, UUID comando, Duration prazoResposta) {
		this.sagaId = novoSagaId;
		this.situacao = SituacaoSaga.EM_ANDAMENTO;
		this.motivo = null;
		this.encerradaEm = null;
		this.iniciadaEm = LocalDateTime.now();
		irPara(EtapaSaga.AGUARDANDO_ORCAMENTO, comando, prazoResposta);
	}

	/** Próximo passo do caminho feliz. {@code comando} e {@code prazoResposta} podem ser nulos. */
	public void avancar(EtapaSaga proximaEtapa, UUID comando, Duration prazoResposta) {
		irPara(proximaEtapa, comando, prazoResposta);
	}

	/** Entra em compensação esperando a confirmação de {@code comando}. */
	public void compensar(EtapaSaga etapaCompensacao, UUID comando, Duration prazoResposta, String motivoCompensacao) {
		this.situacao = SituacaoSaga.COMPENSANDO;
		this.motivo = motivoCompensacao;
		irPara(etapaCompensacao, comando, prazoResposta);
	}

	public void encerrar(SituacaoSaga situacaoFinal, String motivoEncerramento) {
		this.situacao = situacaoFinal;
		if (motivoEncerramento != null) {
			this.motivo = motivoEncerramento;
		}
		this.prazo = null;
		this.encerradaEm = LocalDateTime.now();
	}

	/** Comando reenviado depois de vencido o prazo. */
	public void registrarReenvio(Duration prazoResposta) {
		this.tentativas++;
		this.prazo = LocalDateTime.now().plus(prazoResposta);
	}

	private void irPara(EtapaSaga novaEtapa, UUID comando, Duration prazoResposta) {
		this.etapa = novaEtapa;
		this.comandoMessageId = comando;
		this.tentativas = 0;
		this.prazo = prazoResposta == null ? null : LocalDateTime.now().plus(prazoResposta);
	}

	@PrePersist
	void prePersist() {
		this.criadoEm = LocalDateTime.now();
	}

	@PreUpdate
	void preUpdate() {
		this.atualizadoEm = LocalDateTime.now();
	}
}
