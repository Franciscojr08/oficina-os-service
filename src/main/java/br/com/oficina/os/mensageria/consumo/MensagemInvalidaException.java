package br.com.oficina.os.mensageria.consumo;

/**
 * Mensagem que nunca vai ser processada, por mais que se tente: JSON inválido, campos obrigatórios
 * ausentes, tipo sem manipulador ou fato incompatível com o estado da OS. Vai direto para a DLQ.
 */
public class MensagemInvalidaException extends RuntimeException {

	public MensagemInvalidaException(String message) {
		super(message);
	}

	public MensagemInvalidaException(String message, Throwable cause) {
		super(message, cause);
	}
}
