package br.com.oficina.os.mensageria.porta;

/**
 * Saída da mensageria: entrega uma mensagem ao broker. O domínio não sabe qual é o broker; quem
 * escolhe é o adaptador (hoje {@code RabbitEventoPublisher}). Só o {@code OutboxPublicador} usa esta
 * porta, para que nenhuma mudança de status fique sem evento.
 */
public interface EventoPublisherPort {

	/**
	 * Envia a mensagem e só retorna depois da confirmação do broker.
	 *
	 * @throws FalhaPublicacaoException se o broker não confirmou
	 */
	void publicar(MensagemSaida mensagem);
}
