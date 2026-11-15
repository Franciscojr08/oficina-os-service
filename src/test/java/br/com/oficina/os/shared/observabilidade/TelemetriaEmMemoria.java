package br.com.oficina.os.shared.observabilidade;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Telemetria dos testes: guarda o que seria enviado ao New Relic e devolve, como "trace atual", os
 * cabeçalhos configurados em {@link #traceAtual}.
 */
public class TelemetriaEmMemoria implements Telemetria {

	public record Evento(String tipo, Map<String, Object> atributos) {
	}

	private final List<Evento> eventos = new CopyOnWriteArrayList<>();
	private final Map<String, Double> metricas = new ConcurrentHashMap<>();
	private final Map<String, AtomicInteger> contadores = new ConcurrentHashMap<>();
	private final List<Map<String, String>> rastreamentosContinuados = new CopyOnWriteArrayList<>();
	private final List<String> transacoes = new CopyOnWriteArrayList<>();
	private volatile Map<String, String> traceAtual = Map.of();

	public void traceAtual(Map<String, String> cabecalhos) {
		this.traceAtual = Map.copyOf(cabecalhos);
	}

	@Override
	public void registrarEvento(String tipo, Map<String, Object> atributos) {
		eventos.add(new Evento(tipo, Map.copyOf(atributos)));
	}

	@Override
	public void registrarMetrica(String nome, double valor) {
		metricas.put(nome, valor);
	}

	@Override
	public void incrementarContador(String nome) {
		contadores.computeIfAbsent(nome, n -> new AtomicInteger()).incrementAndGet();
	}

	@Override
	public Map<String, String> capturarRastreamento() {
		return traceAtual;
	}

	/**
	 * Como o agent: depois de aceitar o trace recebido, a transação atual passa a fazer parte dele, e
	 * o que for publicado em seguida leva o mesmo trace.
	 */
	@Override
	public void continuarRastreamento(Map<String, String> cabecalhos) {
		rastreamentosContinuados.add(Map.copyOf(cabecalhos));
		if (cabecalhos.containsKey("traceparent")) {
			traceAtual = Map.copyOf(cabecalhos);
		}
	}

	@Override
	public void nomearTransacao(String categoria, String nome) {
		transacoes.add(categoria + "/" + nome);
	}

	public List<Evento> eventos(String tipo) {
		return eventos.stream().filter(evento -> evento.tipo().equals(tipo)).toList();
	}

	public Double metrica(String nome) {
		return metricas.get(nome);
	}

	public int contador(String nome) {
		AtomicInteger total = contadores.get(nome);
		return total == null ? 0 : total.get();
	}

	public List<Map<String, String>> rastreamentosContinuados() {
		return rastreamentosContinuados;
	}

	public List<String> transacoes() {
		return transacoes;
	}
}
