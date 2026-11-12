package br.com.oficina.os.shared.observabilidade;

import com.newrelic.api.agent.NewRelic;
import com.newrelic.api.agent.TransportType;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Telemetria no New Relic. Sem o agent Java anexado ao processo (testes, docker-compose), todas as
 * chamadas viram no-op e {@link #capturarRastreamento()} devolve vazio. No EKS o agent é injetado
 * pelo k8s-agents-operator (k8s/overlays/eks/newrelic-instrumentation.yaml).
 */
@Component
public class NewRelicTelemetria implements Telemetria {

	@Override
	public void registrarEvento(String tipo, Map<String, Object> atributos) {
		NewRelic.getAgent().getInsights().recordCustomEvent(tipo, atributos);
	}

	@Override
	public void registrarMetrica(String nome, double valor) {
		NewRelic.recordMetric(nome, (float) valor);
	}

	@Override
	public void incrementarContador(String nome) {
		NewRelic.incrementCounter(nome);
	}

	@Override
	public Map<String, String> capturarRastreamento() {
		CabecalhosRastreamento cabecalhos = new CabecalhosRastreamento(Map.of());
		NewRelic.getAgent().getTransaction().insertDistributedTraceHeaders(cabecalhos);
		return cabecalhos.comoMapa();
	}

	@Override
	public void continuarRastreamento(Map<String, String> cabecalhos) {
		if (!cabecalhos.isEmpty()) {
			NewRelic.getAgent().getTransaction()
					.acceptDistributedTraceHeaders(TransportType.AMQP, new CabecalhosRastreamento(cabecalhos));
		}
	}

	@Override
	public void nomearTransacao(String categoria, String nome) {
		NewRelic.setTransactionName(categoria, nome);
	}
}
