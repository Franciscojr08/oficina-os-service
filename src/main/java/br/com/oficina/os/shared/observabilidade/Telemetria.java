package br.com.oficina.os.shared.observabilidade;

import java.util.Map;
import java.util.Set;

/**
 * Telemetria de negócio e rastreamento distribuído. Isola a ferramenta (New Relic, em
 * {@link NewRelicTelemetria}) do código de negócio e permite verificar nos testes o que é enviado.
 */
public interface Telemetria {

	/**
	 * Cabeçalhos de rastreamento propagados entre os serviços: W3C Trace Context ({@code traceparent},
	 * {@code tracestate}) e o formato próprio do New Relic ({@code newrelic}).
	 */
	Set<String> CABECALHOS_RASTREAMENTO = Set.of("traceparent", "tracestate", "newrelic");

	/** Evento customizado (no New Relic, consultável por NRQL: {@code FROM <tipo> SELECT ...}). */
	void registrarEvento(String tipo, Map<String, Object> atributos);

	/** Valor de uma métrica num instante (por exemplo, eventos pendentes no outbox). */
	void registrarMetrica(String nome, double valor);

	void incrementarContador(String nome);

	/**
	 * Cabeçalhos do trace em andamento, para seguir junto com uma mensagem. Vazio quando não há trace
	 * (sem o agente, por exemplo).
	 */
	Map<String, String> capturarRastreamento();

	/** Liga a transação atual ao trace de quem enviou a mensagem. */
	void continuarRastreamento(Map<String, String> cabecalhos);

	/** Nome da transação atual (no New Relic, aparece em APM → Transactions). */
	void nomearTransacao(String categoria, String nome);
}
