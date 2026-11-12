package br.com.oficina.os.mensageria.rabbit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.boot.retry.RetryPolicySettings;

class ConsumoRetryConfigTest {

	@Test
	void rejeicaoDefinitivaEmbrulhadaPeloContainerNaoEhRepetida() {
		Throwable erro = new ListenerExecutionFailedException("falhou",
				new AmqpRejectAndDontRequeueException("mensagem inválida"));

		assertThat(ConsumoRetryConfig.deveTentarDeNovo(erro)).isFalse();
	}

	@Test
	void falhaTransitoriaEhRepetida() {
		Throwable erro = new ListenerExecutionFailedException("falhou", new IllegalStateException("banco fora"));

		assertThat(ConsumoRetryConfig.deveTentarDeNovo(erro)).isTrue();
	}

	@Test
	void customizerAplicaOPredicadoNaPoliticaDeRetry() {
		RetryPolicySettings configuracao = new RetryPolicySettings();

		new ConsumoRetryConfig().naoRepetirRejeicaoDefinitiva().customize(configuracao);

		assertThat(configuracao.getExceptionPredicate().test(new AmqpRejectAndDontRequeueException("x"))).isFalse();
		assertThat(configuracao.getExceptionPredicate().test(new IllegalStateException("x"))).isTrue();
	}
}
