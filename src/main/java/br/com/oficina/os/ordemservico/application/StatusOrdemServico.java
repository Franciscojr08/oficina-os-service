package br.com.oficina.os.ordemservico.application;

import lombok.Getter;

import java.util.List;

/**
 * Ciclo de vida da OS: os 9 status do contrato da Fase 4. A partir de EM_DIAGNOSTICO, as transições
 * são disparadas pela Saga, a partir dos eventos do Billing e da Execução. A espera por uma resposta
 * (orçamento sendo gerado, cobrança em andamento) não é um status: fica na etapa da Saga.
 */
@Getter
public enum StatusOrdemServico {
	RECEBIDA("Recebida"),
	EM_DIAGNOSTICO("Em diagnóstico"),
	AGUARDANDO_APROVACAO("Aguardando aprovação"),
	APROVADA("Aprovada"),
	AGUARDANDO_ITENS("Aguardando itens"),
	EM_EXECUCAO("Em execução"),
	FINALIZADA("Finalizada"),
	ENTREGUE("Entregue"),
	CANCELADA("Cancelada");

	private final String descricao;

	StatusOrdemServico(String descricao) {
		this.descricao = descricao;
	}

	public boolean estaEmDiagnostico() {
		return this == EM_DIAGNOSTICO;
	}

	public static List<StatusOrdemServico> statusAtivos() {
		return List.of(
			RECEBIDA,
			EM_DIAGNOSTICO,
			AGUARDANDO_APROVACAO,
			APROVADA,
			AGUARDANDO_ITENS,
			EM_EXECUCAO,
			FINALIZADA
		);
	}

	/** Status a partir dos quais a OS ainda pode ser cancelada (antes de a execução começar). */
	public static List<StatusOrdemServico> statusCancelaveis() {
		return List.of(
			RECEBIDA,
			EM_DIAGNOSTICO,
			AGUARDANDO_APROVACAO,
			APROVADA,
			AGUARDANDO_ITENS
		);
	}
}
