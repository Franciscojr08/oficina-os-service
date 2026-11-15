package br.com.oficina.os.mensageria.porta;

/**
 * Entrada da mensageria: o que o OS Service faz com cada mensagem que o broker entrega. O adaptador
 * (hoje {@code RabbitComandoListener}) converte a mensagem do broker em {@link MensagemRecebida} e
 * chama esta porta; o domínio não conhece AMQP nem SQS.
 */
public interface ComandoListener {

	/**
	 * Trata uma mensagem recebida.
	 *
	 * @throws MensagemRejeitadaException se a mensagem não pode ser processada nunca (vai para a DLQ)
	 */
	void receber(MensagemRecebida mensagem);
}
