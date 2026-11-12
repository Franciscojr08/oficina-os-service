package br.com.oficina.os.mensageria.rabbit;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.boot.amqp.autoconfigure.RabbitListenerRetrySettingsCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Novas tentativas só para falha transitória. Mensagem rejeitada de forma definitiva (inválida ou
 * incompatível com o estado da OS) vai direto para a DLQ: tentar de novo só atrasaria o consumo.
 */
@Configuration
public class ConsumoRetryConfig {

	@Bean
	public RabbitListenerRetrySettingsCustomizer naoRepetirRejeicaoDefinitiva() {
		return configuracao -> configuracao.setExceptionPredicate(ConsumoRetryConfig::deveTentarDeNovo);
	}

	/** O container embrulha a exceção do listener; a rejeição definitiva pode estar em qualquer nível. */
	static boolean deveTentarDeNovo(Throwable erro) {
		for (Throwable causa = erro; causa != null; causa = causa.getCause()) {
			if (causa instanceof AmqpRejectAndDontRequeueException) {
				return false;
			}
		}
		return true;
	}
}
