package br.com.oficina.os.mensageria.porta;

/** O broker não confirmou a mensagem: ela continua pendente no outbox e será enviada de novo. */
public class FalhaPublicacaoException extends RuntimeException {

	public FalhaPublicacaoException(String mensagem, Throwable causa) {
		super(mensagem, causa);
	}
}
