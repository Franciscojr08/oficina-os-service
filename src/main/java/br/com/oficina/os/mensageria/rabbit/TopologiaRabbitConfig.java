package br.com.oficina.os.mensageria.rabbit;

import br.com.oficina.os.mensageria.config.MensageriaProperties;
import br.com.oficina.os.mensageria.evento.Emissor;
import br.com.oficina.os.mensageria.evento.EventoEntrada;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Declara no RabbitMQ o que o OS Service usa. A declaração é idempotente: quem subir primeiro
 * (este serviço, o Billing ou a Execução) cria as exchanges, e os demais só confirmam.
 *
 * <pre>
 * os.eventos (topic)        ← OS Service publica (routing key = tipo do evento)
 * billing.eventos (topic)   ─┐
 * execucao.eventos (topic)  ─┴→ oficina-os-eventos (fila, só os tipos de EventoEntrada)
 *                                   └─ rejeitada → oficina-os-eventos-dlx → oficina-os-eventos-dlq
 * </pre>
 */
@Configuration
public class TopologiaRabbitConfig {

	@Bean
	public Declarables topologiaMensageria(MensageriaProperties propriedades) {
		TopicExchange exchangeOs = new TopicExchange(propriedades.exchangeOs(), true, false);
		TopicExchange exchangeBilling = new TopicExchange(propriedades.exchangeBilling(), true, false);
		TopicExchange exchangeExecucao = new TopicExchange(propriedades.exchangeExecucao(), true, false);
		DirectExchange deadLetter = new DirectExchange(propriedades.exchangeDeadLetter(), true, false);

		Queue filaEntrada = QueueBuilder.durable(propriedades.filaEntrada())
				.deadLetterExchange(propriedades.exchangeDeadLetter())
				.deadLetterRoutingKey(propriedades.filaDeadLetter())
				.build();
		Queue filaDeadLetter = QueueBuilder.durable(propriedades.filaDeadLetter()).build();

		List<Declarable> declaraveis = new ArrayList<>(List.of(
				exchangeOs, exchangeBilling, exchangeExecucao, deadLetter, filaEntrada, filaDeadLetter,
				BindingBuilder.bind(filaDeadLetter).to(deadLetter).with(propriedades.filaDeadLetter())
		));

		for (EventoEntrada evento : EventoEntrada.values()) {
			TopicExchange origem = evento.getEmissor() == Emissor.BILLING ? exchangeBilling : exchangeExecucao;
			Binding binding = BindingBuilder.bind(filaEntrada).to(origem).with(evento.getTipo());
			declaraveis.add(binding);
		}

		return new Declarables(declaraveis);
	}
}
