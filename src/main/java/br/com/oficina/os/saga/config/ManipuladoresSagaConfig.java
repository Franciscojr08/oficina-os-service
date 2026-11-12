package br.com.oficina.os.saga.config;

import br.com.oficina.os.mensageria.consumo.ManipuladorEvento;
import br.com.oficina.os.mensageria.evento.EnvelopeMensagem;
import br.com.oficina.os.mensageria.evento.EventoEntrada;
import br.com.oficina.os.saga.application.OrquestradorSaga;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

/**
 * Liga cada evento recebido do Billing e da Execução ao passo correspondente do orquestrador. A
 * deduplicação, a transação e o envio para a DLQ ficam com o {@code ProcessadorEventos}.
 */
@Configuration
public class ManipuladoresSagaConfig {

	@Bean
	ManipuladorEvento orcamentoGerado(OrquestradorSaga orquestrador) {
		return manipulador(EventoEntrada.ORCAMENTO_GERADO, orquestrador::orcamentoGerado);
	}

	@Bean
	ManipuladorEvento pagamentoConfirmado(OrquestradorSaga orquestrador) {
		return manipulador(EventoEntrada.PAGAMENTO_CONFIRMADO, orquestrador::pagamentoConfirmado);
	}

	@Bean
	ManipuladorEvento pagamentoRecusado(OrquestradorSaga orquestrador) {
		return manipulador(EventoEntrada.PAGAMENTO_RECUSADO, orquestrador::pagamentoRecusado);
	}

	@Bean
	ManipuladorEvento pagamentoEstornado(OrquestradorSaga orquestrador) {
		return manipulador(EventoEntrada.PAGAMENTO_ESTORNADO, orquestrador::pagamentoEstornado);
	}

	@Bean
	ManipuladorEvento execucaoAguardandoItens(OrquestradorSaga orquestrador) {
		return manipulador(EventoEntrada.EXECUCAO_AGUARDANDO_ITENS, orquestrador::execucaoAguardandoItens);
	}

	@Bean
	ManipuladorEvento execucaoIniciada(OrquestradorSaga orquestrador) {
		return manipulador(EventoEntrada.EXECUCAO_INICIADA, orquestrador::execucaoIniciada);
	}

	@Bean
	ManipuladorEvento servicoFinalizado(OrquestradorSaga orquestrador) {
		return manipulador(EventoEntrada.SERVICO_FINALIZADO, orquestrador::servicoFinalizado);
	}

	@Bean
	ManipuladorEvento execucaoFalhou(OrquestradorSaga orquestrador) {
		return manipulador(EventoEntrada.EXECUCAO_FALHOU, orquestrador::execucaoFalhou);
	}

	@Bean
	ManipuladorEvento execucaoFinalizada(OrquestradorSaga orquestrador) {
		return manipulador(EventoEntrada.EXECUCAO_FINALIZADA, orquestrador::execucaoFinalizada);
	}

	static ManipuladorEvento manipulador(EventoEntrada evento, Consumer<EnvelopeMensagem> acao) {
		return new ManipuladorEvento() {
			@Override
			public String tipo() {
				return evento.getTipo();
			}

			@Override
			public void manipular(EnvelopeMensagem envelope) {
				acao.accept(envelope);
			}
		};
	}
}
