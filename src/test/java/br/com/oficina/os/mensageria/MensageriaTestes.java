package br.com.oficina.os.mensageria;

import br.com.oficina.os.mensageria.config.MensageriaProperties;

/** Valores comuns aos testes unitários da mensageria. */
public final class MensageriaTestes {

	private MensageriaTestes() {
	}

	public static MensageriaProperties propriedades() {
		return new MensageriaProperties(
				"os.eventos",
				"billing.eventos",
				"execucao.eventos",
				"oficina-os-eventos",
				new MensageriaProperties.Outbox(false, 1000, 50),
				new MensageriaProperties.Retencao(7, 30)
		);
	}
}
