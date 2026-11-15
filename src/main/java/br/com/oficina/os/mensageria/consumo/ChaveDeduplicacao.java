package br.com.oficina.os.mensageria.consumo;

import br.com.oficina.os.mensageria.evento.EnvelopeMensagem;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Chave que identifica "esta mensagem já foi processada" no inbox.
 *
 * <ul>
 *   <li>Com {@code messageId}, a chave é o próprio id: duas entregas da mesma mensagem têm o mesmo.</li>
 *   <li>Sem ele (Billing e Execução não são obrigados a mandar), a chave é {@code sagaId} mais o hash
 *   de {@code tipo} e {@code dados}. Assim a mesma mensagem entregue duas vezes é descartada, mas dois
 *   {@code ServicoFinalizado} de serviços diferentes na mesma Saga continuam distintos.</li>
 * </ul>
 *
 * <p>Limite do segundo caso: duas mensagens legítimas com o mesmo {@code tipo} e o mesmo {@code dados}
 * na mesma Saga contam como a mesma. Quem precisar repetir um fato idêntico deve mandar
 * {@code messageId}.</p>
 */
public final class ChaveDeduplicacao {

	private ChaveDeduplicacao() {
	}

	public static String de(EnvelopeMensagem envelope) {
		if (envelope.messageId() != null) {
			return envelope.messageId().toString();
		}
		String dados = envelope.dados() == null ? "" : envelope.dados().toString();
		return envelope.sagaId() + ":" + sha256(envelope.tipo() + "\n" + dados);
	}

	private static String sha256(String texto) {
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException e) {
			// SHA-256 faz parte de toda JVM: chegar aqui é erro de ambiente, não da mensagem
			throw new IllegalStateException("SHA-256 indisponível", e);
		}
	}
}
