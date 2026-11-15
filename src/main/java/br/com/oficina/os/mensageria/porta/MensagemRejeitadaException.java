package br.com.oficina.os.mensageria.porta;

/**
 * A mensagem nunca poderá ser processada (inválida ou contraditória com o estado da OS): o
 * adaptador deve mandá-la direto para a dead-letter queue, sem nova tentativa. Qualquer outra
 * exceção do {@link ComandoListener} é falha transitória e pode ser tentada de novo.
 */
public class MensagemRejeitadaException extends RuntimeException {

	public MensagemRejeitadaException(String mensagem, Throwable causa) {
		super(mensagem, causa);
	}
}
