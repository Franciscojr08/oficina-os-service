package br.com.oficina.os.mensageria.evento;

import lombok.Getter;

/**
 * Comandos e eventos que o OS Service publica na exchange {@code os.eventos}. O nome é a routing
 * key e o campo {@code tipo} do envelope.
 */
@Getter
public enum EventoSaida {
	/** Comando para o Billing: OS, cliente, itens e serviços com preço. */
	SOLICITAR_ORCAMENTO("SolicitarOrcamento"),
	/** Comando para o Billing: cobrar o orçamento que o cliente aprovou. */
	COBRAR_PAGAMENTO("CobrarPagamento"),
	/** Comando para a Execução: itens e serviços da OS paga. */
	ENFILEIRAR_EXECUCAO("EnfileirarExecucao"),
	/** Compensação para o Billing: estornar o pagamento. */
	ESTORNAR_PAGAMENTO("EstornarPagamento"),
	/** Compensação para a Execução: devolver estoque e tirar a OS da fila. */
	CANCELAR_EXECUCAO("CancelarExecucao"),
	/** Informativo, publicado a cada mudança de status. */
	ORDEM_SERVICO_STATUS_ALTERADO("OrdemServicoStatusAlterado");

	private final String tipo;

	EventoSaida(String tipo) {
		this.tipo = tipo;
	}
}
