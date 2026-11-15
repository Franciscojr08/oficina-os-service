package br.com.oficina.os.ordemservico.itens.domain;

import br.com.oficina.os.ordemservico.domain.OrdemServico;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Peça ou insumo da OS. O catálogo é do Billing: aqui ficam o id dele (sem FK) e uma cópia do nome. */
@Getter
@Setter
@Entity
@Table(name = "item_ordem_servico")
public class ItemOrdemServico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "quantidade", nullable = false)
    private int quantidade;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "item_nome", nullable = false, length = 120)
    private String itemNome;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ordem_servico_id", nullable = false)
    private OrdemServico ordemServico;
}
