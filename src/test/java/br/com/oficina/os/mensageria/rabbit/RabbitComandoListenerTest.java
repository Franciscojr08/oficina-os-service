package br.com.oficina.os.mensageria.rabbit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import br.com.oficina.os.mensageria.porta.ComandoListener;
import br.com.oficina.os.mensageria.porta.MensagemRecebida;
import br.com.oficina.os.mensageria.porta.MensagemRejeitadaException;
import br.com.oficina.os.shared.observabilidade.CorrelationIdFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;

import java.nio.charset.StandardCharsets;

@ExtendWith(MockitoExtension.class)
class RabbitComandoListenerTest {

	@Mock
	private ComandoListener comandoListener;

	@InjectMocks
	private RabbitComandoListener adaptador;

	@Test
	void deveConverterAMensagemAmqpEEntregarAoDominio() {
		Message mensagem = MessageBuilder.withBody("{\"tipo\":\"OrcamentoGerado\"}".getBytes(StandardCharsets.UTF_8))
				.setMessageId("id-1")
				.setType("OrcamentoGerado")
				.setHeader(CorrelationIdFilter.CABECALHO, "corr-1")
				.setHeader("traceparent", "00-abc-def-01")
				.setHeader("newrelic", "eyJ2Ijp9")
				.setHeader("x-ordem-servico-id", 10L)
				.build();

		adaptador.receber(mensagem);

		ArgumentCaptor<MensagemRecebida> recebida = ArgumentCaptor.forClass(MensagemRecebida.class);
		verify(comandoListener).receber(recebida.capture());
		assertThat(recebida.getValue().corpo()).isEqualTo("{\"tipo\":\"OrcamentoGerado\"}");
		assertThat(recebida.getValue().messageId()).isEqualTo("id-1");
		assertThat(recebida.getValue().tipo()).isEqualTo("OrcamentoGerado");
		assertThat(recebida.getValue().correlationId()).isEqualTo("corr-1");
		assertThat(recebida.getValue().rastreamento())
				.containsOnlyKeys("traceparent", "newrelic")
				.containsEntry("traceparent", "00-abc-def-01");
	}

	@Test
	void mensagemSemCabecalhosChegaSemCorrelationIdENemRastreamento() {
		adaptador.receber(MessageBuilder.withBody("{}".getBytes(StandardCharsets.UTF_8)).build());

		ArgumentCaptor<MensagemRecebida> recebida = ArgumentCaptor.forClass(MensagemRecebida.class);
		verify(comandoListener).receber(recebida.capture());
		assertThat(recebida.getValue().correlationId()).isNull();
		assertThat(recebida.getValue().messageId()).isNull();
		assertThat(recebida.getValue().tipo()).isNull();
		assertThat(recebida.getValue().rastreamento()).isEmpty();
	}

	@Test
	void rejeicaoDefinitivaViraRejeicaoSemRequeueParaIrParaADlq() {
		MensagemRejeitadaException rejeicao = new MensagemRejeitadaException("status não permite", null);
		doThrow(rejeicao).when(comandoListener).receber(org.mockito.ArgumentMatchers.any());

		assertThatThrownBy(() -> adaptador.receber(MessageBuilder.withBody("{}".getBytes(StandardCharsets.UTF_8)).build()))
				.isInstanceOf(AmqpRejectAndDontRequeueException.class)
				.hasMessage("status não permite")
				.hasCause(rejeicao);
	}

	@Test
	void falhaTransitoriaSobeSemAlteracaoParaOContainerTentarDeNovo() {
		IllegalStateException banco = new IllegalStateException("banco fora");
		doThrow(banco).when(comandoListener).receber(org.mockito.ArgumentMatchers.any());

		assertThatThrownBy(() -> adaptador.receber(MessageBuilder.withBody("{}".getBytes(StandardCharsets.UTF_8)).build()))
				.isSameAs(banco);
	}
}
