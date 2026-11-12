package br.com.oficina.os.ordemservico.domain;

import br.com.oficina.os.cliente.domain.Cliente;
import br.com.oficina.os.ordemservico.application.StatusOrdemServico;
import br.com.oficina.os.veiculo.domain.Veiculo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "ordem_servico")
public class OrdemServico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo", nullable = false, unique = true)
    private String codigo;

    @Column(name = "descricao_problema")
    private String descricaoProblema;

    @Column(name = "observacoes_gerais")
    private String observacoesGerais;

    @Column(name = "descricao_servicos_executados", nullable = false)
    private String descricaoServicosExecutados;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StatusOrdemServico status;

    @Column(name = "data_cadastro", nullable = false)
    private LocalDateTime dataCadastro;

    @Column(name = "data_solicitacao_orcamento")
    private LocalDateTime dataSolicitacaoOrcamento;

    @Column(name = "data_envio_aprovacao")
    private LocalDateTime dataEnvioAprovacao;

    @Column(name = "data_aprovacao")
    private LocalDateTime dataAprovacao;

    @Column(name = "data_pagamento")
    private LocalDateTime dataPagamento;

    @Column(name = "data_inicio_execucao")
    private LocalDateTime dataInicioExecucao;

    @Column(name = "data_fim_execucao")
    private LocalDateTime dataFimExecucao;

    @Column(name = "data_entregue")
    private LocalDateTime dataEntregue;

    @Column(name = "data_cancelada")
    private LocalDateTime dataCancelada;

    /** Id do orçamento no Billing (referência lógica, sem FK: outro serviço, outro banco). */
    @Column(name = "orcamento_id")
    private Long orcamentoId;

    /** Id do pagamento no Billing (referência lógica, sem FK). */
    @Column(name = "pagamento_id")
    private Long pagamentoId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "veiculo_id", nullable = false)
    private Veiculo veiculo;

    @PrePersist
    public void prePersist() {
        this.dataCadastro = LocalDateTime.now();
        this.status = StatusOrdemServico.RECEBIDA;
    }
}
