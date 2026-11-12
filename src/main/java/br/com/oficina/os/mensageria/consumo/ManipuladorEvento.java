package br.com.oficina.os.mensageria.consumo;

import br.com.oficina.os.mensageria.evento.EnvelopeMensagem;

/**
 * Reação a um tipo de evento recebido. Roda dentro da transação que registra a mensagem como
 * processada: se lançar exceção, nada fica gravado e a mensagem pode ser reprocessada.
 */
public interface ManipuladorEvento {

	/** Tipo do evento tratado (campo {@code tipo} do envelope). */
	String tipo();

	void manipular(EnvelopeMensagem envelope);
}
